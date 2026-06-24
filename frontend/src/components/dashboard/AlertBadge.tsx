import clsx from 'clsx'

interface Props {
  active: boolean
}

export default function AlertBadge({ active }: Props) {
  if (!active) return null
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold',
        'bg-error-container text-error'
      )}
    >
      <span className="relative flex h-2 w-2">
        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-error opacity-75" />
        <span className="relative inline-flex rounded-full h-2 w-2 bg-error" />
      </span>
      ALERT
    </span>
  )
}
