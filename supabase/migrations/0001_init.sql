-- ── tables ────────────────────────────────────────────────────────────────────

create table sites (
  id         text        primary key,
  name       text        not null,
  created_at timestamptz default now()
);

create table devices (
  id      text primary key,
  site_id text references sites(id),
  label   text,
  mode    text check (mode in ('ble', 'network'))
);

create table events (
  id         uuid        primary key default gen_random_uuid(),
  device_id  text,
  site_id    text,
  station    text,
  probe_id   text,
  type       text        check (type in ('temp', 'timer', 'corrective_action', 'alert')),
  value      jsonb,
  ts         timestamptz,
  payload    jsonb,
  created_at timestamptz default now()
);

-- ── indexes ───────────────────────────────────────────────────────────────────

create index events_site_ts  on events (site_id, ts);
create index events_type_ts  on events (type,    ts);

-- ── RLS (permissive — hackathon only) ─────────────────────────────────────────

alter table sites   enable row level security;
alter table devices enable row level security;
alter table events  enable row level security;

create policy sites_anon_all   on sites   for all to anon using (true) with check (true);
create policy devices_anon_all on devices for all to anon using (true) with check (true);
create policy events_anon_all  on events  for all to anon using (true) with check (true);

-- ── Realtime ──────────────────────────────────────────────────────────────────

alter publication supabase_realtime add table events;
