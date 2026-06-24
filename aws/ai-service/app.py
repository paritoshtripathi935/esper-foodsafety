import logging
import os
from typing import Optional

import httpx
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

import bedrock as _bedrock
import s3_store

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="Esper AI Service")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── request / response models ─────────────────────────────────────────────────

class StructureNoteRequest(BaseModel):
    transcript: str
    device_id: str
    site_id: str
    station: str


class NarrateRequest(BaseModel):
    alert: dict
    corrective_action: dict
    temp_slice: list


class AskRequest(BaseModel):
    question: str
    site_id: str
    from_: Optional[str] = None
    to: Optional[str] = None

    class Config:
        populate_by_name = True

    def __init__(self, **data):
        if "from" in data:
            data["from_"] = data.pop("from")
        super().__init__(**data)


# ── routes ────────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/ai/structure-note")
def structure_note(req: StructureNoteRequest):
    """AI-4: Real Bedrock /ai/structure-note with tool-use. AI-3 stub superseded."""
    return _bedrock.structure_note(
        transcript=req.transcript,
        device_id=req.device_id,
        site_id=req.site_id,
        station=req.station,
    )


@app.post("/ai/narrate")
def narrate(req: NarrateRequest):
    """AI-6: Audit-grade narrative paragraph from incident bundle."""
    narrative = _bedrock.narrate(
        alert=req.alert,
        corrective_action=req.corrective_action,
        temp_slice=req.temp_slice,
    )
    return {"narrative": narrative}


@app.post("/ai/ask")
def ask(req: AskRequest):
    """AI-8 [CUT-1]: Natural-language Q&A over a Supabase event slice."""
    supabase_url = os.environ.get("SUPABASE_URL", "")
    service_key = os.environ.get("SUPABASE_SERVICE_KEY", "")

    headers = {
        "apikey": service_key,
        "Authorization": f"Bearer {service_key}",
    }
    params: dict = {
        "site_id": f"eq.{req.site_id}",
        "order": "ts.asc",
        "limit": "200",
    }
    if req.from_:
        params["ts"] = f"gte.{req.from_}"
    if req.to:
        params["ts"] = f"lte.{req.to}"

    try:
        resp = httpx.get(
            f"{supabase_url}/rest/v1/events",
            headers=headers,
            params=params,
            timeout=10.0,
        )
        resp.raise_for_status()
        events = resp.json()
    except Exception as exc:
        logging.error("Supabase query error: %s", exc)
        raise HTTPException(status_code=502, detail="Failed to fetch events from Supabase")

    # Keep context window manageable — last 100 events
    if len(events) > 100:
        events = events[-100:]

    answer = _bedrock.ask(question=req.question, events=events)
    return {"answer": answer}


@app.post("/pdf")
async def store_pdf(
    file: UploadFile = File(...),
    site_id: str = Form(...),
    date: str = Form(...),
):
    """AI-7: Accept PDF bytes and store to S3 at haccp/{site_id}/{date}.pdf."""
    data = await file.read()
    key = f"haccp/{site_id}/{date}.pdf"
    return s3_store.put_object(key=key, data=data, content_type="application/pdf")
