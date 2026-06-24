import { useMemo } from 'react'
import type { FoodSafetyEvent } from '../types'

export function useAlerts(
  events: FoodSafetyEvent[],
  siteFilter: string | null
): FoodSafetyEvent[] {
  return useMemo(() => {
    const filtered = siteFilter
      ? events.filter((e) => e.site_id === siteFilter)
      : events
    return filtered
      .filter((e) => e.type === 'alert')
      .sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime())
  }, [events, siteFilter])
}
