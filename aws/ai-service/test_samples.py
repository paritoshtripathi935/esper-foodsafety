#!/usr/bin/env python3
"""AI-5: Severity classification test harness — run against deployed service."""
import os
import sys
import httpx

BASE = os.environ.get("AI_API_BASE", "http://localhost:8000")

SAMPLES = [
    {
        "label": "Minor deviation (expect low or medium)",
        "transcript": (
            "Cooler door was left ajar for about 10 minutes during morning prep. "
            "Temperature rose to 39°F. Door was closed, temperature returned to 36°F "
            "within 15 minutes. No product affected."
        ),
        "device_id": "tablet-01",
        "site_id": "site-eastgate",
        "station": "walk-in-cooler-1",
        "expect": {"low", "medium"},
    },
    {
        "label": "Product discard / overnight failure (expect high or critical)",
        "transcript": (
            "Walk-in cooler compressor failed overnight. Temperature reached 58°F by 6am. "
            "All chicken, beef, and dairy products discarded — approximately 40 lbs of product. "
            "Maintenance called. Replacement unit ordered. Event reported to health department."
        ),
        "device_id": "tablet-01",
        "site_id": "site-eastgate",
        "station": "walk-in-cooler-1",
        "expect": {"high", "critical"},
    },
    {
        "label": "Mid-severity breach, no discard (expect medium or high)",
        "transcript": (
            "Cooler temperature was 44°F at 8am inspection — 3 degrees above threshold. "
            "Moved temperature-sensitive items to backup cooler. Called HVAC technician. "
            "Cooler returned to 38°F by noon. Reviewed product — no discard needed, all within safe range."
        ),
        "device_id": "tablet-01",
        "site_id": "site-eastgate",
        "station": "walk-in-cooler-1",
        "expect": {"medium", "high"},
    },
]


def run() -> int:
    passed = 0
    print(f"Running {len(SAMPLES)} samples against {BASE}\n")
    for s in SAMPLES:
        payload = {k: s[k] for k in ("transcript", "device_id", "site_id", "station")}
        try:
            resp = httpx.post(f"{BASE}/ai/structure-note", json=payload, timeout=30)
            resp.raise_for_status()
            result = resp.json()
        except Exception as exc:
            print(f"[ERROR] {s['label']}: {exc}\n")
            continue

        severity = result.get("severity", "")
        ok = severity in s["expect"]
        if ok:
            passed += 1
        status = "PASS" if ok else "FAIL"
        print(f"[{status}] {s['label']}")
        print(f"       severity={severity!r}  (expected one of {s['expect']})")
        print(f"       action_taken : {result.get('action_taken', '')[:80]}")
        print(f"       root_cause   : {result.get('root_cause', '')[:80]}")
        print(f"       disposition  : {result.get('disposition', '')[:80]}")
        print()

    print(f"Result: {passed}/{len(SAMPLES)} passed")
    return 0 if passed == len(SAMPLES) else 1


if __name__ == "__main__":
    sys.exit(run())
