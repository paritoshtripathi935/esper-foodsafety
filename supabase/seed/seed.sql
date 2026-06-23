-- Idempotent seed — safe to re-run at any time.
-- DATA-3: Eastgate site + tablet device
-- DATA-5: Westwood site + VM device

insert into sites (id, name) values
  ('site-eastgate', 'Eastgate Kitchen'),
  ('site-westwood', 'Westwood Café')
on conflict (id) do nothing;

insert into devices (id, site_id, label, mode) values
  ('tablet-01', 'site-eastgate', 'Walk-in Cooler Tablet', 'ble'),
  ('vm-01',     'site-westwood', 'Esper VM Probe',        'network')
on conflict (id) do nothing;
