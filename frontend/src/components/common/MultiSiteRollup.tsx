import clsx from 'clsx'
import type { StationState, FoodSafetyEvent, Site } from '../../types'

interface Props {
  sites: Site[]
  stations: StationState[]
  alerts: FoodSafetyEvent[]
  selectedSiteId: string | null
  onSelectSite: (siteId: string) => void
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

export default function MultiSiteRollup({ sites, stations, alerts, selectedSiteId, onSelectSite }: Props) {
  if (sites.length <= 1) return null

  return (
    <div className="flex flex-col gap-5">
      <h2 className="text-sm font-medium text-on-surface-variant uppercase tracking-wide">All Sites</h2>
      <div className="flex gap-3 flex-wrap">
      {sites.map((site) => {
        const siteStations = stations.filter((s) => s.site_id === site.id)
        const siteAlerts = alerts.filter((a) => a.site_id === site.id)
        const openTimers = siteStations.filter((s) => s.openTimer !== null).length
        const status = worstStatus(siteStations)

        const isSelected = site.id === selectedSiteId

        return (
          <button
            key={site.id}
            onClick={() => onSelectSite(site.id)}
            className={clsx(
              'flex items-center gap-3 px-4 py-3 rounded-xl border transition-colors text-left',
              isSelected
                ? 'bg-primary/10 border-primary text-on-surface'
                : 'bg-surface-container border-outline-variant hover:border-primary/50 hover:bg-surface-container-high',
            )}
          >
            <span className={clsx('w-2.5 h-2.5 rounded-full shrink-0', STATUS_DOT[status])} />
            <div>
              <p className="text-sm font-semibold text-on-surface">{site.name}</p>
              <p className="text-xs text-on-surface-variant mt-0.5">
                {siteAlerts.length} alert{siteAlerts.length !== 1 ? 's' : ''} · {openTimers} timer
                {openTimers !== 1 ? 's' : ''}
              </p>
            </div>
          </button>
        )
      })}
      </div>
    </div>
  )
}
