# SafeTemp

**Food-safety HACCP compliance for restaurants — real-time temperature/timer board on a managed kiosk tablet, with auto-generated audit records and PDF compliance reports.**

> Hackathon project — in active development (scaffold/work-in-progress).

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Probe Sources                                                          │
│  ┌──────────────────┐   BLE GATT    ┌───────────────────────────────┐  │
│  │ probe-simulator  │ ────────────► │  kiosk-app (Kotlin / Android) │  │
│  │  (Android phone) │               │  Esper MDM – locked kiosk     │  │
│  └──────────────────┘               └──────────────┬────────────────┘  │
│  ┌──────────────────┐  HTTP POST                   │ REST POST /events  │
│  │ Network probe    │ ─────────────────────────────┘                   │
│  │ (no-BLE devices) │                                                   │
│  └──────────────────┘                                                   │
└─────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
                          ┌─────────────────────────┐
                          │   Supabase              │
                          │  • Postgres (events,    │
                          │    sites, devices)      │
                          │  • Realtime channel     │
                          │  • Edge Function /ingest│
                          └────────────┬────────────┘
                                       │ webhook / REST
                                       ▼
                          ┌─────────────────────────┐
                          │   AWS EC2 (FastAPI)     │
                          │  ai-service             │
                          │  • Bedrock / Claude     │   ← LLM called ONLY here
                          │    structure-note       │
                          │    narrate              │
                          │  • S3 – HACCP PDF store │
                          └────────────┬────────────┘
                                       │
                                       ▼
                          ┌─────────────────────────┐
                          │  React Dashboard        │
                          │  (Vite + Tailwind)      │
                          │  Live board, audit log, │
                          │  PDF download           │
                          └─────────────────────────┘
```

**Dual probe source:** the kiosk reads temperature via BLE GATT from the simulator app (custom service UUID `12345678-1234-5678-1234-56789abcdef0`). Devices without Bluetooth post `temp` events directly to Supabase over HTTP (network-probe mode).

**LLM scope:** Claude (via AWS Bedrock) is invoked exclusively from the EC2 service — never from the Android device or the browser.

**MDM:** tablets are enrolled in Esper MDM for single-app kiosk lockdown and remote provisioning.

---

## Repo Structure

```
esper-foodsafety/
├── android/
│   ├── kiosk-app/          # Kotlin tablet app — reads BLE probes, displays station board, POSTs events
│   └── probe-simulator/    # Kotlin phone app — simulates a BLE temperature probe (GATT server)
├── supabase/
│   ├── migrations/         # Postgres schema (0001_init.sql — events, sites, devices tables)
│   ├── functions/ingest/   # Edge Function for offline-replay batch ingest
│   └── seed/               # Demo seed data
├── aws/
│   ├── ai-service/         # FastAPI service: /ai/structure-note, /ai/narrate, /pdf (Bedrock + S3)
│   └── infra/              # EC2 user-data script + IAM instance role policy
├── frontend/               # React + Vite + Tailwind dashboard (live board, audit log, PDF export)
├── shared/
│   ├── schema/
│   │   ├── events.ts       # TypeScript type definitions — cross-track event contract
│   │   └── events.py       # Python equivalent (Pydantic models)
│   └── contracts/
│       └── api.md          # Full API contract (Supabase REST + Edge Function + AI service endpoints)
└── docker-compose.yml      # Local dev compose (frontend + ai-service)
```

---

## Event Model

All tracks share a single `events` table as the source of truth. The canonical type definition lives in `shared/schema/events.ts` (TypeScript) and `shared/schema/events.py` (Python/Pydantic). See `shared/contracts/api.md` for the full HTTP contract between components.

Key fields: `device_id`, `site_id`, `station`, `probe_id`, `type` (`temp | timer | corrective_action | alert`), `value`, `ts` (ISO 8601), and a `payload` object for corrective-action fields (`action_taken`, `root_cause`, `disposition`, `severity`, `narrative`).

---

## Getting Started

### Environment Variables

Copy the example files and fill in your values:

```bash
# Root-level (used by docker-compose / local scripts)
cp aws/ai-service/.env.example .env

# Frontend
cp frontend/.env.example frontend/.env   # if present, else create manually
```

Key variables:

| Variable | Where | Description |
|---|---|---|
| `SUPABASE_URL` | frontend `.env`, ai-service `.env` | Supabase project URL |
| `SUPABASE_ANON_KEY` | frontend `.env` | Public anon key for PostgREST + Realtime |
| `SUPABASE_SERVICE_KEY` | ai-service `.env` | Service-role key for server-side writes |
| `AWS_REGION` | ai-service `.env` | e.g. `us-east-1` |
| `BEDROCK_MODEL_ID` | ai-service `.env` | e.g. `anthropic.claude-3-5-sonnet-20241022-v2:0` |
| `S3_BUCKET` | ai-service `.env` | Bucket for HACCP PDFs |
| `AI_API_BASE` | frontend `.env` | EC2 service base URL, e.g. `http://<ec2-dns>:8000` |

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Requires Node 18+. Talks to Supabase directly for data/realtime and to `AI_API_BASE` for AI features.

### AWS AI Service

```bash
cd aws/ai-service
pip install -r requirements.txt
uvicorn app:app --reload --port 8000
```

Requires Python 3.11+ and AWS credentials with Bedrock + S3 access (see `aws/infra/iam-instance-role-policy.json`).

### Android

Open `android/` in Android Studio. Build `kiosk-app` or `probe-simulator` via Gradle:

```bash
cd android
./gradlew :kiosk-app:assembleDebug
./gradlew :probe-simulator:assembleDebug
```

### Supabase

```bash
supabase start                          # local dev
supabase db push                        # apply migrations
supabase functions serve ingest         # local Edge Function
```

---

## Status

Hackathon project — all components are scaffolded and wired at the interface level; full implementation is in active development.

---

*Hackathon project — no license. All rights reserved.*
