import { Link } from 'react-router-dom'

interface Crumb {
  label: string
  to?: string
}

interface Props {
  trail: Crumb[]
}

export default function Breadcrumb({ trail }: Props) {
  return (
    <nav className="flex items-center gap-1 text-sm text-on-surface-variant flex-wrap">
      {trail.map((crumb, i) => (
        <span key={i} className="flex items-center gap-1">
          {i > 0 && (
            <svg className="w-3 h-3 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          )}
          {crumb.to ? (
            <Link to={crumb.to} className="hover:text-on-surface transition-colors">
              {crumb.label}
            </Link>
          ) : (
            <span className="text-on-surface font-semibold">{crumb.label}</span>
          )}
        </span>
      ))}
    </nav>
  )
}
