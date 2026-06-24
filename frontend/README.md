# SafeTemp Manager Dashboard

React + Vite + TypeScript + Tailwind frontend for the SafeTemp food-safety HACCP kiosk system.

## Setup

```bash
cd frontend
cp .env.example .env
# Fill in .env values (see below)
npm install
npm run dev
```

Opens at http://localhost:5173.

## Environment Variables

| Variable | Description |
|---|---|
| `VITE_SUPABASE_URL` | Supabase project URL (e.g. `https://xxx.supabase.co`) |
| `VITE_SUPABASE_ANON_KEY` | Supabase anon/public key |
| `VITE_AI_API_BASE` | EC2 FastAPI base URL (e.g. `http://ec2-x.compute-1.amazonaws.com:8000`) |

Values are already populated in `.env` for the hackathon demo.

## Seeding Demo Data

From the browser console on any page:

```js
await window.seedDemo()
```

Or click the **Seed demo data** button in the empty state of the station board / alerts feed.

This clears the `events` table and inserts a believable day for `site-eastgate`:
- 30 temp readings on 4 stations from −8h to now
- `walk-in-cooler-1` ramps 38→48°F starting at −2h
- 1 alert event at threshold crossing (41°F)
- 1 corrective action 4 min later with pre-populated narrative
- 1 active hold timer for "Chicken — lunch" on `hot-hold-1`

## Features

| WEB | Feature |
|---|---|
| WEB-1 | Vite scaffold, Tailwind dark theme, kiosk palette |
| WEB-2 | Supabase client + connection badge |
| WEB-3 | Realtime events feed (Postgres Changes INSERT) |
| WEB-4 | Live station board with TempCard / TimerCard / AlertBadge |
| WEB-5 | Alerts feed, newest-first, slide-in animation |
| WEB-6 | Site selector (shown when >1 site) |
| WEB-7 | Alert detail modal with corrective action + AI narrative |
| WEB-8 | HACCP PDF export → local download + S3 upload via EC2 |
| WEB-9 | Multi-site rollup header (shown when >1 site) |
| WEB-10 | Empty/error states, ErrorToast, seed-demo utility |

## Troubleshooting

**Supabase: error badge**
- Verify `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` in `.env`
- The `events` table requires an RLS policy permitting anon SELECT/INSERT/DELETE:
  ```sql
  create policy "events_anon_all" on public.events
    for all to anon using (true) with check (true);
  ```

**Realtime not updating**
- Ensure Realtime is enabled on the `public.events` table in Supabase dashboard
  (Database → Replication → enable `events`)
- The channel is `public:events` — only one global channel is opened (no quota issues)

**PDF export: S3 toast not appearing**
- EC2 `/pdf` endpoint must be running and reachable from the browser
- If EC2 is down, the file still downloads locally and a toast explains the S3 failure

**seed-demo fails with RLS error**
- The anon DELETE policy must cover all rows. The seed uses:
  `.delete().neq('id', '00000000-...')` which requires a permissive DELETE policy.
  The `events_anon_all` policy above covers this.
