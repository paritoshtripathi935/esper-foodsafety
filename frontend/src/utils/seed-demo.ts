import { supabase } from '../services/supabase'
import type { FoodSafetyEvent } from '../types'

const SITE = 'site-eastgate'
const DEVICE = 'seed-device-001'

const STATIONS = [
  { name: 'walk-in-cooler-1', baseTemp: 37, maxTemp: 41 },
  { name: 'walk-in-cooler-2', baseTemp: 38, maxTemp: 41 },
  { name: 'prep-table-cold', baseTemp: 36, maxTemp: 41 },
  { name: 'hot-hold-1', baseTemp: 140, maxTemp: 135 },  // hot station, min threshold
]

function jitter(n: number, range: number) {
  return n + (Math.random() - 0.5) * range
}

export async function seedDemo(): Promise<void> {
  console.log('[seedDemo] Clearing existing events…')

  // Clear — RLS policy events_anon_all permits DELETE for anon
  const { error: delErr } = await supabase
    .from('events')
    .delete()
    .neq('id', '00000000-0000-0000-0000-000000000000')

  if (delErr) {
    console.error('[seedDemo] Clear failed:', delErr)
    throw delErr
  }

  const now = Date.now()
  const hoursAgo = (h: number) => new Date(now - h * 60 * 60 * 1000).toISOString()
  const minsAgo = (m: number) => new Date(now - m * 60 * 1000).toISOString()

  const events: Omit<FoodSafetyEvent, 'id'>[] = []

  // 30 temp readings spread over -8h to now for cold stations
  // walk-in-cooler-1: ramps from 38→48 starting at -2h
  for (let i = 0; i < 30; i++) {
    const hoursBack = 8 - (i * 8) / 30
    const ts = new Date(now - hoursBack * 60 * 60 * 1000).toISOString()

    for (const station of STATIONS) {
      let temp: number
      if (station.name === 'walk-in-cooler-1' && hoursBack <= 2) {
        const rampProgress = (2 - hoursBack) / 2
        temp = 38 + rampProgress * 10 + jitter(0, 0.3)
      } else if (station.name === 'hot-hold-1') {
        temp = jitter(140, 3)
      } else {
        temp = jitter(station.baseTemp, 1.5)
      }

      events.push({
        device_id: DEVICE,
        site_id: SITE,
        station: station.name,
        probe_id: `probe-${station.name}`,
        type: 'temp',
        value: Math.round(temp * 10) / 10,
        ts,
        payload: {},
      })
    }
  }

  // Alert: walk-in-cooler-1 threshold crossed ~2 hours ago
  const alertId = crypto.randomUUID()
  const alertTs = minsAgo(120)
  events.push({
    device_id: DEVICE,
    site_id: SITE,
    station: 'walk-in-cooler-1',
    probe_id: 'probe-walk-in-cooler-1',
    type: 'alert',
    value: 41.8,
    ts: alertTs,
    payload: {
      threshold: 41,
      rule_id: 'rule-walk-in-cooler-1',
      alert_event_id: alertId,
    },
  })

  // Corrective action 4 minutes after alert
  const caTs = minsAgo(116)
  events.push({
    device_id: DEVICE,
    site_id: SITE,
    station: 'walk-in-cooler-1',
    probe_id: null,
    type: 'corrective_action',
    value: 0,
    ts: caTs,
    payload: {
      alert_event_id: alertId,
      action_taken: 'Moved product to backup cooler',
      root_cause: 'Compressor failure — unit lost cooling capacity',
      disposition: 'Discarded 2 trays of dairy products exceeding 2-hour threshold',
      severity: 'high',
      narrative:
        'At approximately 12:00, walk-in cooler 1 temperature rose above the 41°F HACCP limit, ' +
        'reaching 41.8°F. Immediate action was taken: all temperature-sensitive products were ' +
        'relocated to the backup cooler within 4 minutes. Root cause identified as compressor ' +
        'failure. Two trays of dairy product that had been in the danger zone were discarded per ' +
        'HACCP protocol. Unit has been flagged for maintenance. No further violations observed.',
    },
  })

  // Timer: chicken hold on hot-hold-1, started 45 min ago, 2 hour hold
  const timerTs = minsAgo(45)
  events.push({
    device_id: DEVICE,
    site_id: SITE,
    station: 'hot-hold-1',
    probe_id: null,
    type: 'timer',
    value: 0,
    ts: timerTs,
    payload: {
      event: 'start',
      batch_label: 'Chicken — lunch',
      hold_minutes: 120,
    },
  })

  console.log(`[seedDemo] Inserting ${events.length} events…`)

  // Insert in batches of 200 to avoid payload limits
  for (let i = 0; i < events.length; i += 200) {
    const batch = events.slice(i, i + 200)
    const { error: insErr } = await supabase.from('events').insert(batch)
    if (insErr) {
      console.error('[seedDemo] Insert failed:', insErr)
      throw insErr
    }
  }

  console.log('[seedDemo] Done. Seeded a full demo day for site-eastgate.')
}

// Expose on window for console access
if (typeof window !== 'undefined') {
  (window as unknown as Record<string, unknown>).seedDemo = seedDemo
}
