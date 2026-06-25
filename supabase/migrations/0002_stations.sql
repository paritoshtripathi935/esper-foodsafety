create table if not exists stations (
  id         text        primary key,
  site_id    text        references sites(id),
  slug       text        not null,
  name       text        not null,
  zone       text        check (zone in ('cold', 'hot', 'frozen', 'prep')),
  max_temp_f numeric,
  min_temp_f numeric,
  created_at timestamptz default now(),
  unique (site_id, slug)
);

alter table stations enable row level security;

do $$ begin
  if not exists (
    select 1 from pg_policies where tablename = 'stations' and policyname = 'stations_anon_all'
  ) then
    create policy stations_anon_all on stations for all to anon using (true) with check (true);
  end if;
end $$;

alter publication supabase_realtime add table stations;
