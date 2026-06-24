import clsx from 'clsx'
import type { StationState } from '../../types'
import AlertBadge from './AlertBadge'
import TimerCard from './TimerCard'

const TEMP_MAX = 41
const TEMP_WARN = 40

function statusColor(temp: number | null, hasAlert: boolean) {
  if (hasAlert || (temp !== null && temp > TEMP_MAX)) return 'alert'
  if (temp !== null && temp > TEMP_WARN) return 'warn'
  return 'ok'
}

const STATUS_LABELS = { ok: 'OK', warn: 'WARM', alert: 'ALERT' } as const

const STATUS_CLASSES = {
  ok: {
    pill: 'bg-primary/20 text-primary',
    temp: 'text-primary',
    border: 'border-outline-variant',
  },
  warn: {
    pill: 'bg-tertiary/20 text-tertiary',
    temp: 'text-tertiary',
    border: 'border-tertiary/40',
  },
  alert: {
    pill: 'bg-error-container text-error',
    temp: 'text-error',
    border: 'border-error/60',
  },
}

interface Props {
  state: StationState
}

export default function TempCard({ state }: Props) {
  const { station, latestTemp, openTimer, activeAlert } = state
  const status = statusColor(latestTemp, activeAlert !== null)
  const cls = STATUS_CLASSES[status]

  return (
    <div
      className={clsx(
        'flex flex-col gap-3 p-4 rounded-xl border bg-surface-container',
        cls.border,
        status === 'alert' && 'shadow-lg shadow-error/10'
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs font-medium text-on-surface-variant uppercase tracking-wider truncate">
            {station.replace(/-/g, ' ')}
          </p>
          <div className="flex items-baseline gap-1 mt-1">
            {latestTemp !== null ? (
              <>
                <span className={clsx('font-mono text-4xl font-bold tabular-nums', cls.temp)}>
                  {latestTemp.toFixed(1)}
                </span>
                <span className={clsx('font-mono text-lg', cls.temp)}>°F</span>
              </>
            ) : (
              <span className="font-mono text-2xl text-on-surface-variant">—</span>
            )}
          </div>
        </div>
        <div className="flex flex-col items-end gap-1.5 shrink-0">
          <span className={clsx('px-2 py-0.5 rounded-full text-xs font-semibold', cls.pill)}>
            {STATUS_LABELS[status]}
          </span>
          <AlertBadge active={activeAlert !== null} />
        </div>
      </div>

      {openTimer && <TimerCard timer={openTimer} />}
    </div>
  )
}
