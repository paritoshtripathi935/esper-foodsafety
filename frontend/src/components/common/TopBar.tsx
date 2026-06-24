import SiteSelector from './SiteSelector'
import PDFExportButton from '../pdf/PDFExportButton'
import type { FoodSafetyEvent } from '../../types'

interface Props {
  sites: string[]
  selectedSite: string | null
  onSelectSite: (site: string | null) => void
  supabaseStatus: 'connected' | 'error' | 'pending'
  events: FoodSafetyEvent[]
  selectedDate: string
  onDateChange: (date: string) => void
  onToast?: (msg: string) => void
}

const STATUS_BADGE = {
  connected: 'bg-primary/20 text-primary',
  error: 'bg-error-container text-error',
  pending: 'bg-surface-variant text-on-surface-variant',
}

export default function TopBar({
  sites,
  selectedSite,
  onSelectSite,
  supabaseStatus,
  events,
  selectedDate,
  onDateChange,
  onToast,
}: Props) {
  return (
    <header className="flex items-center gap-4 px-5 py-3 border-b border-outline-variant bg-surface-container shrink-0">
      <div className="flex items-center gap-2 mr-auto">
        <svg className="w-5 h-5 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
        </svg>
        <span className="font-semibold text-on-surface tracking-tight">SafeTemp</span>
        <span className="text-on-surface-variant mx-1">·</span>
        <span className="text-sm text-on-surface-variant">Manager Dashboard</span>
      </div>

      <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${STATUS_BADGE[supabaseStatus]}`}>
        Supabase: {supabaseStatus}
      </span>

      <SiteSelector sites={sites} selected={selectedSite} onChange={onSelectSite} />

      <input
        type="date"
        value={selectedDate}
        onChange={(e) => onDateChange(e.target.value)}
        className="px-3 py-1.5 rounded-lg bg-surface-container-high border border-outline-variant
          text-on-surface text-sm focus:outline-none focus:border-primary cursor-pointer"
      />

      <PDFExportButton events={events} siteId={selectedSite ?? 'all'} date={selectedDate} onToast={onToast} />
    </header>
  )
}
