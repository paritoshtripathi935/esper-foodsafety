import { useMemo } from 'react'
import type { FoodSafetyEvent } from '../types'

export function useSites(events: FoodSafetyEvent[]): string[] {
  return useMemo(() => {
    const sites = new Set<string>()
    for (const e of events) {
      if (e.site_id) sites.add(e.site_id)
    }
    return Array.from(sites).sort()
  }, [events])
}
