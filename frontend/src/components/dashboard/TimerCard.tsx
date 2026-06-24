import { useEffect, useState } from 'react'
import { parseISO } from 'date-fns'
import clsx from 'clsx'

interface Props {
  timer: {
    batch_label: string
    hold_minutes: number
    started_at: string
  }
}

function formatCountdown(seconds: number): string {
  const abs = Math.abs(seconds)
  const h = Math.floor(abs / 3600)
  const m = Math.floor((abs % 3600) / 60)
  const s = abs % 60
  const sign = seconds < 0 ? '+' : ''
  if (h > 0) return `${sign}${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${sign}${m}:${String(s).padStart(2, '0')}`
}

export default function TimerCard({ timer }: Props) {
  const { batch_label, hold_minutes, started_at } = timer
  const endTime = parseISO(started_at).getTime() + hold_minutes * 60 * 1000

  const [remaining, setRemaining] = useState(() =>
    Math.round((endTime - Date.now()) / 1000)
  )

  useEffect(() => {
    const id = setInterval(() => {
      setRemaining(Math.round((endTime - Date.now()) / 1000))
    }, 1000)
    return () => clearInterval(id)
  }, [endTime])

  const expired = remaining < 0

  return (
    <div
      className={clsx(
        'flex items-center justify-between px-3 py-2 rounded-lg text-sm',
        expired
          ? 'bg-error-container/30 border border-error/30'
          : 'bg-surface-container-high border border-outline-variant'
      )}
    >
      <div className="flex items-center gap-2 min-w-0">
        <svg className="shrink-0 w-3.5 h-3.5 text-on-surface-variant" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span className="truncate text-on-surface-variant">{batch_label}</span>
      </div>
      <span
        className={clsx(
          'font-mono font-semibold tabular-nums shrink-0 ml-2',
          expired ? 'text-error' : 'text-primary'
        )}
      >
        {formatCountdown(remaining)}
      </span>
    </div>
  )
}
