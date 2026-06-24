import { useMemo } from 'react'
import type { FoodSafetyEvent, StationState } from '../types'

export function useDerivedStationState(
  events: FoodSafetyEvent[],
  siteFilter: string | null
): StationState[] {
  return useMemo(() => {
    const filtered = siteFilter
      ? events.filter((e) => e.site_id === siteFilter)
      : events

    const byStation = new Map<string, FoodSafetyEvent[]>()
    for (const e of filtered) {
      const key = `${e.site_id}::${e.station}`
      if (!byStation.has(key)) byStation.set(key, [])
      byStation.get(key)!.push(e)
    }

    const states: StationState[] = []

    for (const [key, stationEvents] of byStation) {
      const [site_id, station] = key.split('::')
      const sorted = [...stationEvents].sort(
        (a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime()
      )

      // Latest temp
      const tempEvent = sorted.find((e) => e.type === 'temp')
      const latestTemp = tempEvent != null ? Number(tempEvent.value) : null

      // Open timer: latest timer/start with no timer/complete or timer/breach after it
      const timerStarts = sorted.filter(
        (e) => e.type === 'timer' && (e.payload as Record<string, unknown>)?.event === 'start'
      )
      let openTimer: StationState['openTimer'] = null
      for (const t of timerStarts) {
        const p = t.payload as Record<string, unknown>
        const batchLabel = String(p?.batch_label ?? '')
        const holdMinutes = Number(p?.hold_minutes ?? 0)
        const startedAt = t.ts
        const tStart = new Date(t.ts).getTime()
        const hasClose = sorted.some(
          (e) =>
            e.type === 'timer' &&
            (e.payload as Record<string, unknown>)?.batch_label === batchLabel &&
            ((e.payload as Record<string, unknown>)?.event === 'complete' ||
              (e.payload as Record<string, unknown>)?.event === 'breach') &&
            new Date(e.ts).getTime() > tStart
        )
        if (!hasClose) {
          openTimer = { batch_label: batchLabel, hold_minutes: holdMinutes, started_at: startedAt }
          break
        }
      }

      // Active alert: latest type='alert' with no later corrective_action for same station
      const alerts = sorted.filter((e) => e.type === 'alert')
      let activeAlert: FoodSafetyEvent | null = null
      if (alerts.length > 0) {
        const latestAlert = alerts[0]
        const tAlert = new Date(latestAlert.ts).getTime()
        const hasCA = sorted.some(
          (e) =>
            e.type === 'corrective_action' &&
            new Date(e.ts).getTime() > tAlert
        )
        if (!hasCA) activeAlert = latestAlert
      }

      states.push({ station, site_id, latestTemp, openTimer, activeAlert })
    }

    return states.sort((a, b) => a.station.localeCompare(b.station))
  }, [events, siteFilter])
}
