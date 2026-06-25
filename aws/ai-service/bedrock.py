import json
import logging
import os

import boto3

_client = None

SEVERITY_VALID = {"low", "medium", "high", "critical"}

# ── low-level client ──────────────────────────────────────────────────────────

def _get_client():
    global _client
    if _client is None:
        _client = boto3.client(
            "bedrock-runtime",
            region_name=os.environ.get("AWS_REGION", "us-east-1"),
        )
    return _client


def invoke(prompt: str, system: str = "", max_tokens: int = 1024) -> str:
    """Send a prompt to the configured Claude model and return the text response."""
    model_id = os.environ["BEDROCK_MODEL_ID"]
    client = _get_client()

    body = {
        "anthropic_version": "bedrock-2023-05-31",
        "max_tokens": max_tokens,
        "messages": [{"role": "user", "content": prompt}],
    }
    if system:
        body["system"] = system

    response = client.invoke_model(
        modelId=model_id,
        contentType="application/json",
        accept="application/json",
        body=json.dumps(body),
    )
    result = json.loads(response["body"].read())
    return result["content"][0]["text"]


def invoke_with_tool(prompt: str, system: str, tool: dict, max_tokens: int = 1024) -> dict:
    """Invoke Claude with a single tool and return the tool-use result dict."""
    model_id = os.environ["BEDROCK_MODEL_ID"]
    client = _get_client()

    body = {
        "anthropic_version": "bedrock-2023-05-31",
        "max_tokens": max_tokens,
        "system": system,
        "tools": [tool],
        "tool_choice": {"type": "tool", "name": tool["name"]},
        "messages": [{"role": "user", "content": prompt}],
    }

    response = client.invoke_model(
        modelId=model_id,
        contentType="application/json",
        accept="application/json",
        body=json.dumps(body),
    )
    result = json.loads(response["body"].read())
    for block in result.get("content", []):
        if block.get("type") == "tool_use":
            return block["input"]
    raise ValueError("No tool_use block in Bedrock response")


# ── /ai/structure-note (AI-4 + AI-5) ─────────────────────────────────────────

_STRUCTURE_NOTE_TOOL = {
    "name": "structure_corrective_action",
    "description": "Extract structured fields from a food-safety corrective-action transcript.",
    "input_schema": {
        "type": "object",
        "properties": {
            "action_taken": {
                "type": "string",
                "description": "What was done to correct the issue",
            },
            "root_cause": {
                "type": "string",
                "description": "Why the issue occurred",
            },
            "disposition": {
                "type": "string",
                "description": "What happened to the affected product (e.g. discarded, held, used)",
            },
            "severity": {
                "type": "string",
                "enum": ["low", "medium", "high", "critical"],
                "description": (
                    "Severity of the food-safety incident. "
                    "Use high/critical for illness reports, large temperature violations, or product discards. "
                    "Use low/medium for minor deviations corrected quickly with no product loss."
                ),
            },
        },
        "required": ["action_taken", "root_cause", "disposition", "severity"],
    },
}

_STRUCTURE_NOTE_SYSTEM = (
    "You are a food-safety HACCP assistant. Extract structured fields from the corrective-action transcript. "
    "severity MUST be exactly one of: low, medium, high, critical — never any other value. "
    "Bias toward high or critical when product was discarded, illness was reported, or a significant temperature breach occurred."
)


def structure_note(transcript: str, device_id: str, site_id: str, station: str) -> dict:
    """Call Bedrock tool-use to structure a corrective-action transcript.

    Never raises — returns a safe high-severity fallback on any error.
    """
    prompt = (
        f"Station: {station} | Device: {device_id} | Site: {site_id}\n\n"
        f"Corrective-action transcript:\n{transcript}"
    )
    try:
        raw = invoke_with_tool(
            prompt=prompt,
            system=_STRUCTURE_NOTE_SYSTEM,
            tool=_STRUCTURE_NOTE_TOOL,
            max_tokens=512,
        )
        severity = raw.get("severity", "high")
        if severity not in SEVERITY_VALID:
            logging.warning("Bedrock returned invalid severity %r — coercing to 'high'", severity)
            severity = "high"
        return {
            "action_taken": raw.get("action_taken", ""),
            "root_cause": raw.get("root_cause", ""),
            "disposition": raw.get("disposition", ""),
            "severity": severity,
            "narrative": "",
        }
    except Exception as exc:
        logging.error("structure_note Bedrock error: %s", exc)
        return {
            "action_taken": "Unable to parse — manual review required",
            "root_cause": "Bedrock unavailable",
            "disposition": "unknown",
            "severity": "high",
            "narrative": "",
        }


# ── /ai/narrate (AI-6) ────────────────────────────────────────────────────────

_NARRATE_SYSTEM = (
    "You are a food-safety HACCP assistant writing official audit records. "
    "Write a single concise paragraph (3–5 sentences) grounded strictly in the provided incident data. "
    "Do not invent facts, times, quantities, or names not present in the input. "
    "Use past tense. Cite actual values from the data (temperatures, times, product names)."
)


def narrate(alert: dict, corrective_action: dict, temp_slice: list) -> str:
    """Generate an audit-grade narrative paragraph for an incident."""
    prompt = (
        "Generate an audit narrative for this food-safety incident.\n\n"
        f"Alert: {json.dumps(alert)}\n"
        f"Corrective action: {json.dumps(corrective_action)}\n"
        f"Temperature readings: {json.dumps(temp_slice)}"
    )
    return invoke(prompt, system=_NARRATE_SYSTEM, max_tokens=512)


# ── /ai/ask (AI-8 CUT-1) ─────────────────────────────────────────────────────

_ASK_SYSTEM = """\
You are a food-safety HACCP assistant. Answer questions using only the provided event log data.

Formatting rules — follow these exactly:
- Use Markdown. Structure your answer with clear headings (## for main sections, ### for sub-sections).
- Use a table when listing multiple stations, readings, or events with comparable attributes.
- Use bullet points for short enumerations that don't suit a table.
- Use **bold** for station names, key temperatures, and action items.
- End with a brief "### Summary" section (2–4 sentences) that gives the big picture.
- Do NOT use horizontal rules (---). Use headings to separate sections.
- Do NOT use emojis.
- If a question cannot be answered from the data, say so clearly under a "### Data Limitation" heading.
- Keep language factual and audit-appropriate. No filler phrases.\
"""


def ask(question: str, events: list) -> str:
    """Answer a natural-language question grounded in an event log slice."""
    prompt = (
        f"Event log ({len(events)} events):\n{json.dumps(events, indent=2)}\n\n"
        f"Question: {question}"
    )
    return invoke(prompt, system=_ASK_SYSTEM, max_tokens=2048)
