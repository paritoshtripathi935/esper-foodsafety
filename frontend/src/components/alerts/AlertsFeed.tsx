import { formatDistanceToNow, parseISO, format } from 'date-fns'
import clsx from 'clsx'
import type { FoodSafetyEvent } from '../../types'
import EmptyState from '../common/EmptyState'

interface Props {
  alerts: FoodSafetyEvent[]
  allEvents: FoodSafetyEvent[]
  onSelectAlert: (alert: FoodSafetyEvent) => void
  onSeedDemo?: () => void
}

function isActive(alert: FoodSafetyEvent, allEvents: FoodSafetyEvent[]): boolean {
  const tAlert = new Date(alert.ts).getTime()
  return !allEvents.some(
    (e) =>
      e.type === 'corrective_action' &&
      e.station === alert.station &&
      new Date(e.ts).getTime() > tAlert
  )
}

export default function AlertsFeed({ alerts, allEvents, onSelectAlert, onSeedDemo }: Props) {
  if (alerts.length === 0) {
    return (
      <div className="flex flex-col h-full">
        <div className="px-4 py-3 border-b border-outline-variant">
          <h2 className="text-sm font-semibold text-on-surface">Alerts</h2>
        </div>
        <div className="flex-1 flex items-center justify-center">
          <EmptyState
            title="No alerts"
            body="All stations are operating within safe limits."
            onSeed={onSeedDemo}
          />
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      <div className="px-4 py-3 border-b border-outline-variant">
        <h2 className="text-sm font-semibold text-on-surface">
          Alerts
          <span className="ml-2 px-1.5 py-0.5 rounded-full text-xs bg-error-container text-error">
            {alerts.length}
          </span>
        </h2>
      </div>

      <div className="flex-1 overflow-y-auto divide-y divide-outline-variant">
        {alerts.map((alert) => {
          const p = alert.payload as Record<string, unknown>
          const temp = Number(alert.value)
          const threshold = p?.threshold != null ? Number(p.threshold) : null
          const active = isActive(alert, allEvents)

          return (
            <button
              key={alert.id}
              onClick={() => onSelectAlert(alert)}
              className={clsx(
                'w-full text-left px-4 py-3 animate-slide-in-top transition-colors',
                'hover:bg-surface-container-high',
                active ? 'bg-error-container/20' : 'bg-transparent'
              )}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-xs font-semibold uppercase tracking-wide text-on-surface-variant truncate">
                    {alert.station.replace(/-/g, ' ')}
                  </p>
                  <div className="flex items-baseline gap-1.5 mt-0.5">
                    <span className="font-mono font-bold text-error text-lg tabular-nums">
                      {temp.toFixed(1)}°F
                    </span>
                    {threshold !== null && (
                      <span className="text-xs text-on-surface-variant">
                        (limit {threshold}°F)
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-on-surface-variant mt-0.5">
                    {format(parseISO(alert.ts), 'HH:mm')} ·{' '}
                    {formatDistanceToNow(parseISO(alert.ts), { addSuffix: true })}
                  </p>
                </div>
                <div className="shrink-0 flex flex-col items-end gap-1">
                  {active && (
                    <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-xs font-semibold bg-error-container text-error">
                      <span className="relative flex h-1.5 w-1.5">
                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-error opacity-75" />
                        <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-error" />
                      </span>
                      Active
                    </span>
                  )}
                  <svg className="w-4 h-4 text-on-surface-variant" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                  </svg>
                </div>
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
