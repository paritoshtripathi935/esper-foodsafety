# API Contract

Two hosts (see `plan/00-overview.md`):
- **Supabase** — data + realtime (events, sites, ingest).
- **AWS EC2 AI service** (`AI_API_BASE`, e.g. `http://<ec2-public-dns>:8000`) — Bedrock + S3.

The LLM is called **only** from the EC2 service, never from the device or browser.

---

## Supabase (DATA)

### POST `/rest/v1/events`  (PostgREST)
Ingest a single event from device or simulator.
**Body:** `FoodSafetyEvent` (see `shared/schema/events.ts`).
**Headers:** `apikey`, `Authorization: Bearer <anon>`, `Content-Type: application/json`, `Prefer: return=minimal`.

### POST `/functions/v1/ingest`  (Edge Function)
Offline-replay / batch: array of `FoodSafetyEvent`, inserted in `ts` order.
**Returns:** `{ inserted: number }`.

### GET `/rest/v1/events?site_id=&station=&type=&ts=gte.&ts=lte.`
Query events for the dashboard / PDF.

### GET `/rest/v1/sites`  /  `/rest/v1/devices`
List enrolled sites / devices.

### Realtime channel `events`
`postgres_changes` INSERT subscription drives the live dashboard.

---

## AWS EC2 AI service (AI)

### POST `/ai/structure-note`
**Body:** `{ transcript: string, device_id: string, site_id: string, station: string }`
**Returns:** `{ action_taken, root_cause, disposition, severity }` (severity ∈ `low|medium|high|critical`).
Bedrock Claude via boto3, tool-use enforced JSON.

### POST `/ai/narrate`
**Body:** `{ alert, corrective_action, temp_slice }` (incident bundle).
**Returns:** `{ narrative: string }` — audit-grade paragraph.

### POST `/ai/ask`   `[CUT-1]`
**Body:** `{ question: string, site_id: string, from: string, to: string }`
**Returns:** `{ answer: string }` — "Ask the log" Q&A over the event slice (no embeddings).

### POST `/pdf`
**Body:** PDF bytes (multipart `file`) + `{ site_id, date }`.
**Returns:** `{ bucket, key }` — stored at `haccp/{site_id}/{date}.pdf` in S3.
*(Alt: `/pdf-presign` returning a presigned PUT URL — requires S3 CORS for a direct browser PUT.)*

### GET `/health`
Liveness check.

---

## BLE GATT (device-local, not HTTP)

Simulator advertises a custom service UUID with one **Temperature** characteristic (`READ | NOTIFY`).

- **Service UUID:** `12345678-1234-5678-1234-56789abcdef0`
- **Temperature Characteristic UUID:** `12345678-1234-5678-1234-56789abcdef1`
- **Encoding:** 4-byte Float (IEEE 754), Little-Endian.

Network-probe mode (Esper VMs, no BLE) reaches the same data path by POSTing `temp` events to Supabase `/rest/v1/events`.
