interface Props {
  data: number[]
  status: 'ok' | 'warn' | 'alert'
}

export default function MiniSparkline({ data, status }: Props) {
  if (data.length < 2) return null
  const w = 80, h = 24
  const min = Math.min(...data), max = Math.max(...data)
  const range = max - min || 1
  const points = data
    .map((v, i) => {
      const x = (i / (data.length - 1)) * w
      const y = h - ((v - min) / range) * h
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
  const color =
    status === 'alert'
      ? 'var(--color-error)'
      : status === 'warn'
        ? 'var(--color-tertiary)'
        : 'var(--color-primary)'
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} className="opacity-80">
      <polyline
        points={points}
        fill="none"
        stroke={color}
        strokeWidth={1.5}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </svg>
  )
}
