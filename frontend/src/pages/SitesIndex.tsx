import { useMemo } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { formatDistanceToNow, parseISO, isToday } from 'date-fns'
import clsx from 'clsx'
import { useDerivedStationState } from '../hooks/useDerivedStationState'
import EmptyState from '../components/common/EmptyState'
import type { FoodSafetyEvent, Site, Station, Device } from '../types'

// ─── outlet context ───────────────────────────────────────────────────────────

interface ShellContext {
  events: FoodSafetyEvent[]
  sites: Site[]
  devices: Device[]
  stationRegistry: Station[]
  selectedSite: string | null
  setSelectedSite: (s: string | null) => void
  addToast: (msg: string) => void
  handleSeedDemo: () => Promise<void>
}

// ─── per-site status ──────────────────────────────────────────────────────────

type SiteStatus = 'alert' | 'warn' | 'ok'

const STATUS_DOT: Record<SiteStatus, string> = {
  alert: 'bg-error animate-pulse-slow',
  warn:  'bg-tertiary',
  ok:    'bg-primary',
}
const STATUS_RING: Record<SiteStatus, string> = {
  alert: 'border-error/40',
  warn:  'border-tertiary/30',
  ok:    'border-outline-variant',
}

// ─── SiteCard ─────────────────────────────────────────────────────────────────

interface SiteCardProps {
  site: Site
  events: FoodSafetyEvent[]
  devices: Device[]
  stationRegistry: Station[]
}

function SiteCard({ site, events, devices, stationRegistry }: SiteCardProps) {
  const navigate = useNavigate()

  const stationStates = useDerivedStationState(events, site.id, stationRegistry)

  const siteStatus = useMemo((): SiteStatus => {
    if (stationStates.some((s) => s.activeAlert !== null)) return 'alert'
    if (stationStates.some((s) => s.latestTemp !== null && s.latestTemp > 40)) return 'warn'
    return 'ok'
  }, [stationStates])

  const alertsToday = useMemo(
    () =>
      events.filter(
        (e) => e.type === 'alert' && e.site_id === site.id && isToday(parseISO(e.ts))
      ).length,
    [events, site.id]
  )

  const lastEventTs = useMemo(() => {
    const siteEvents = events.filter((e) => e.site_id === site.id)
    if (siteEvents.length === 0) return null
    return siteEvents.reduce((best, e) =>
      e.ts > best.ts ? e : best
    ).ts
  }, [events, site.id])

  const deviceCount = devices.filter((d) => d.site_id === site.id).length

  return (
    <div
      onClick={() => navigate(`/sites/${site.id}`)}
      className={clsx(
        'flex flex-col gap-4 p-5 rounded-2xl border bg-surface-container cursor-pointer',
        'hover:bg-surface-container-high transition-colors',
        STATUS_RING[siteStatus]
      )}
    >
      {/* Top row: status dot + name */}
      <div className="flex items-start gap-3">
        <span className={clsx('mt-1 w-2.5 h-2.5 rounded-full shrink-0', STATUS_DOT[siteStatus])} />
        <div className="min-w-0">
          <p className="font-semibold text-on-surface truncate">{site.name}</p>
          {site.address && (
            <p className="text-xs text-on-surface-variant mt-0.5 truncate">{site.address}</p>
          )}
        </div>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-3 gap-2">
        <div className="flex flex-col items-center p-2 rounded-lg bg-surface-container-high">
          <span className="font-mono font-bold text-lg text-on-surface tabular-nums">
            {stationStates.length}
          </span>
          <span className="text-[10px] text-on-surface-variant mt-0.5">stations</span>
        </div>
        <div className="flex flex-col items-center p-2 rounded-lg bg-surface-container-high">
          <span className="font-mono font-bold text-lg text-on-surface tabular-nums">
            {deviceCount}
          </span>
          <span className="text-[10px] text-on-surface-variant mt-0.5">devices</span>
        </div>
        <div className={clsx(
          'flex flex-col items-center p-2 rounded-lg',
          alertsToday > 0 ? 'bg-error-container/30' : 'bg-surface-container-high'
        )}>
          <span className={clsx(
            'font-mono font-bold text-lg tabular-nums',
            alertsToday > 0 ? 'text-error' : 'text-on-surface'
          )}>
            {alertsToday}
          </span>
          <span className="text-[10px] text-on-surface-variant mt-0.5">alerts today</span>
        </div>
      </div>

      {/* Last data footer */}
      <div className="flex items-center justify-between">
        <p className="text-xs text-on-surface-variant">
          {lastEventTs
            ? `Last data ${formatDistanceToNow(parseISO(lastEventTs), { addSuffix: true })}`
            : 'No data yet'}
        </p>
        <svg className="w-4 h-4 text-on-surface-variant" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
      </div>
    </div>
  )
}

// ─── SitesIndex ───────────────────────────────────────────────────────────────

export default function SitesIndex() {
  const { events, sites, devices, stationRegistry, handleSeedDemo } =
    useOutletContext<ShellContext>()

  return (
    <div className="flex flex-col gap-6 p-6">
      {/* Header */}
      <div>
        <h1 className="text-xl font-bold text-on-surface">Sites</h1>
        <p className="text-sm text-on-surface-variant mt-0.5">
          {sites.length} site{sites.length !== 1 ? 's' : ''} monitored
        </p>
      </div>

      {sites.length === 0 ? (
        <EmptyState
          title="No sites yet"
          body="Sites appear here once registered in Supabase. Seed demo data to get started."
          onSeed={handleSeedDemo}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {sites.map((site) => (
            <SiteCard
              key={site.id}
              site={site}
              events={events}
              devices={devices}
              stationRegistry={stationRegistry}
            />
          ))}
        </div>
      )}
    </div>
  )
}
