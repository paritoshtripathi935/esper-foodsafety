import { useEffect, useState } from 'react'
import { format, parseISO } from 'date-fns'
import clsx from 'clsx'
import type { FoodSafetyEvent, AlertSeverity } from '../../types'
import { narrateIncident } from '../../services/ai'

interface Props {
  alert: FoodSafetyEvent
  allEvents: FoodSafetyEvent[]
  onClose: () => void
}

const SEVERITY_CLASSES: Record<AlertSeverity, string> = {
  low: 'bg-primary/20 text-primary',
  medium: 'bg-tertiary/20 text-tertiary',
  high: 'bg-error-container text-error',
  critical: 'bg-error-container text-error border border-error/50',
}

function findCorrectiveAction(
  alert: FoodSafetyEvent,
  allEvents: FoodSafetyEvent[]
): FoodSafetyEvent | null {
  const tAlert = new Date(alert.ts).getTime()
  const TEN_MIN = 10 * 60 * 1000

  // Preferred: payload.alert_event_id match
  const byId = allEvents.find(
    (e) =>
      e.type === 'corrective_action' &&
      (e.payload as Record<string, unknown>)?.alert_event_id === alert.id
  )
  if (byId) return byId

  // Fallback: same station within ±10 min
  return (
    allEvents.find(
      (e) =>
        e.type === 'corrective_action' &&
        e.station === alert.station &&
        Math.abs(new Date(e.ts).getTime() - tAlert) <= TEN_MIN
    ) ?? null
  )
}

export default function AlertDetail({ alert, allEvents, onClose }: Props) {
  const [narrative, setNarrative] = useState<string | null>(null)
  const [narrativeLoading, setNarrativeLoading] = useState(false)

  const ca = findCorrectiveAction(alert, allEvents)
  const caPayload = ca?.payload as Record<string, unknown> | undefined
  const severity = (caPayload?.severity ?? 'high') as AlertSeverity

  useEffect(() => {
    if (!ca) return
    const existingNarrative = caPayload?.narrative as string | undefined
    if (existingNarrative) {
      setNarrative(existingNarrative)
      return
    }

    setNarrativeLoading(true)
    const tAlert = new Date(alert.ts).getTime()
    const THIRTY_MIN = 30 * 60 * 1000
    const tempSlice = allEvents.filter(
      (e) =>
        e.type === 'temp' &&
        e.station === alert.station &&
        Math.abs(new Date(e.ts).getTime() - tAlert) <= THIRTY_MIN
    )
    narrateIncident(alert, caPayload ?? {}, tempSlice)
      .then((text) => setNarrative(text))
      .catch((err) => setNarrative(`[AI unavailable: ${err.message}]`))
      .finally(() => setNarrativeLoading(false))
  }, [alert.id, ca?.id])

  const alertPayload = alert.payload as Record<string, unknown>

  return (
    <div className="fixed inset-0 z-40 flex items-end sm:items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-background/80 backdrop-blur-sm"
        onClick={onClose}
      />

      <div className="relative w-full max-w-lg bg-surface-container rounded-2xl border border-outline-variant shadow-2xl max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-start gap-3 p-5 border-b border-outline-variant">
          <div className="flex-1 min-w-0">
            <p className="text-xs font-medium text-on-surface-variant uppercase tracking-wider">
              Alert · {alert.station.replace(/-/g, ' ')}
            </p>
            <p className="font-mono text-2xl font-bold text-error mt-1">
              {Number(alert.value).toFixed(1)}°F
            </p>
            {alertPayload?.threshold != null && (
              <p className="text-sm text-on-surface-variant mt-0.5">
                Threshold: {Number(alertPayload.threshold).toFixed(0)}°F
              </p>
            )}
            <p className="text-xs text-on-surface-variant mt-1">
              {format(parseISO(alert.ts), 'MMM d, yyyy HH:mm:ss')}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant"
            aria-label="Close"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="p-5 flex flex-col gap-5">
          {/* Corrective Action */}
          {ca ? (
            <div className="flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-on-surface">Corrective Action</h3>
                <span className={clsx('px-2 py-0.5 rounded-full text-xs font-semibold capitalize', SEVERITY_CLASSES[severity])}>
                  {severity}
                </span>
              </div>

              <div className="grid grid-cols-1 gap-3">
                {!!caPayload?.action_taken && (
                  <div className="p-3 rounded-lg bg-surface-container-high">
                    <p className="text-xs text-on-surface-variant mb-1">Action taken</p>
                    <p className="text-sm text-on-surface">{String(caPayload.action_taken)}</p>
                  </div>
                )}
                {!!caPayload?.root_cause && (
                  <div className="p-3 rounded-lg bg-surface-container-high">
                    <p className="text-xs text-on-surface-variant mb-1">Root cause</p>
                    <p className="text-sm text-on-surface">{String(caPayload.root_cause)}</p>
                  </div>
                )}
                {!!caPayload?.disposition && (
                  <div className="p-3 rounded-lg bg-surface-container-high">
                    <p className="text-xs text-on-surface-variant mb-1">Disposition</p>
                    <p className="text-sm text-on-surface">{String(caPayload.disposition)}</p>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="p-3 rounded-lg bg-surface-variant text-sm text-on-surface-variant text-center">
              No corrective action logged yet.
            </div>
          )}

          {/* AI Narrative */}
          {ca && (
            <div>
              <h3 className="text-sm font-semibold text-on-surface mb-2">HACCP Narrative</h3>
              {narrativeLoading ? (
                <div className="flex items-center gap-2 text-sm text-on-surface-variant">
                  <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                  Generating narrative…
                </div>
              ) : narrative ? (
                <div className="p-3 rounded-lg bg-surface-container-high text-sm text-on-surface leading-relaxed whitespace-pre-wrap">
                  {narrative}
                </div>
              ) : (
                <p className="text-sm text-on-surface-variant italic">No narrative yet.</p>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
