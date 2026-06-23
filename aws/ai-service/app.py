import os
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional

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
    from_: str = None  # "from" is a reserved word — alias below
    to: str = None

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
    # Implemented in AI-4
    raise HTTPException(status_code=501, detail="Not implemented — see AI-4")


@app.post("/ai/narrate")
def narrate(req: NarrateRequest):
    # Implemented in AI-4
    raise HTTPException(status_code=501, detail="Not implemented — see AI-4")


@app.post("/ai/ask")
def ask(body: dict):
    # CUT-1 — implemented in AI-7
    raise HTTPException(status_code=501, detail="Not implemented — see AI-7 [CUT-1]")


@app.post("/pdf")
async def store_pdf(
    file: UploadFile = File(...),
    site_id: str = Form(...),
    date: str = Form(...),
):
    # Implemented in AI-3
    raise HTTPException(status_code=501, detail="Not implemented — see AI-3")
