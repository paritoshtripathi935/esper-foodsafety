interface Props {
  sites: string[]
  selected: string | null
  onChange: (site: string | null) => void
}

export default function SiteSelector({ sites, selected, onChange }: Props) {
  if (sites.length <= 1) return null

  return (
    <select
      value={selected ?? ''}
      onChange={(e) => onChange(e.target.value || null)}
      className="px-3 py-1.5 rounded-lg bg-surface-container-high border border-outline-variant
        text-on-surface text-sm focus:outline-none focus:border-primary cursor-pointer"
    >
      <option value="">All sites</option>
      {sites.map((s) => (
        <option key={s} value={s}>
          {s.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())}
        </option>
      ))}
    </select>
  )
}
