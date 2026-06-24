import clsx from 'clsx'
import type { StationState, FoodSafetyEvent } from '../../types'

interface Props {
  sites: string[]
  stations: StationState[]
  alerts: FoodSafetyEvent[]
  onSelectSite: (site: string) => void
}

function worstStatus(stations: StationState[]): 'alert' | 'warn' | 'ok' {
  if (stations.some((s) => s.activeAlert !== null)) return 'alert'
  if (stations.some((s) => s.latestTemp !== null && s.latestTemp > 40)) return 'warn'
  return 'ok'
}

const STATUS_DOT = {
  ok: 'bg-primary',
  warn: 'bg-tertiary',
  alert: 'bg-error animate-pulse-slow',
}

export default function MultiSiteRollup({ sites, stations, alerts, onSelectSite }: Props) {
  if (sites.length <= 1) return null

  return (
    <div className="flex gap-3 flex-wrap">
      {sites.map((siteId) => {
        const siteStations = stations.filter((s) => s.site_id === siteId)
        const siteAlerts = alerts.filter((a) => a.site_id === siteId)
        const openTimers = siteStations.filter((s) => s.openTimer !== null).length
        const status = worstStatus(siteStations)

        return (
          <button
            key={siteId}
            onClick={() => onSelectSite(siteId)}
            className="flex items-center gap-3 px-4 py-3 rounded-xl bg-surface-container border border-outline-variant
              hover:border-primary/50 hover:bg-surface-container-high transition-colors text-left"
          >
            <span className={clsx('w-2.5 h-2.5 rounded-full shrink-0', STATUS_DOT[status])} />
            <div>
              <p className="text-sm font-semibold text-on-surface">
                {siteId.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())}
              </p>
              <p className="text-xs text-on-surface-variant mt-0.5">
                {siteAlerts.length} alert{siteAlerts.length !== 1 ? 's' : ''} · {openTimers} timer{openTimers !== 1 ? 's' : ''}
              </p>
            </div>
          </button>
        )
      })}
    </div>
  )
}
