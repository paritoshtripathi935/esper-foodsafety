import { useMemo } from 'react'
import { Link, NavLink, Navigate, useParams } from 'react-router-dom'
import { format, formatDistanceToNow, parseISO, isToday } from 'date-fns'
import clsx from 'clsx'
import { useRegistry } from '../hooks/useRegistry'
import { useRealtimeEvents } from '../hooks/useRealtimeEvents'
import { useDerivedStationState } from '../hooks/useDerivedStationState'
import TempCard from '../components/dashboard/TempCard'
import PDFExportButton from '../components/pdf/PDFExportButton'
import type { Device } from '../hooks/useRegistry'

// ─── device helpers ──────────────────────────────────────────────────────────

type DeviceStatus = 'online' | 'stale' | 'offline'

function deviceStatus(lastSeen: string | null): DeviceStatus {
  if (!lastSeen) return 'offline'
  const ageMs = Date.now() - new Date(lastSeen).getTime()
  if (ageMs < 2 * 60_000) return 'online'
  if (ageMs < 10 * 60_000) return 'stale'
  return 'offline'
}

const STATUS_PILL: Record<DeviceStatus, string> = {
  online: 'bg-primary/20 text-primary',
  stale: 'bg-tertiary/20 text-tertiary',
  offline: 'bg-error-container text-error',
}

const KIND_LABEL: Record<Device['kind'], string> = {
  kiosk: '📱 Kiosk',
  'probe-sim': '🔌 Probe-sim',
}

// ─── DeviceCard ───────────────────────────────────────────────────────────────

interface DeviceCardProps {
  device: Device
  eventCountToday: number
  siteId: string
}

function DeviceCard({ device, eventCountToday, siteId }: DeviceCardProps) {
  const ds = deviceStatus(device.last_seen)
  const lastSeenLabel = device.last_seen
    ? formatDistanceToNow(parseISO(device.last_seen), { addSuffix: true })
    : 'never'

  return (
    <div className="flex flex-col gap-3 p-4 rounded-xl border border-outline-variant bg-surface-container">
      {/* Top row: name + status pill */}
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold text-on-surface truncate">{device.name}</p>
          <p className="text-xs text-on-surface-variant mt-0.5">{KIND_LABEL[device.kind]}</p>
        </div>
        <span className={clsx('shrink-0 px-2 py-0.5 rounded-full text-xs font-semibold', STATUS_PILL[ds])}>
          {ds}
        </span>
      </div>

      {/* Stats row */}
      <div className="flex items-center gap-4 text-xs text-on-surface-variant">
        <span>
          <span className="font-medium text-on-surface">{eventCountToday}</span> events today
        </span>
        <span className="truncate" title={device.last_seen ?? undefined}>
          Last seen {lastSeenLabel}
        </span>
      </div>

      {/* Open link */}
      <Link
        to={`/sites/${siteId}/devices/${device.id}`}
        className="self-start flex items-center gap-1 text-xs font-semibold text-primary
          hover:text-primary/80 transition-colors"
      >
        Open
        <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
      </Link>
    </div>
  )
}

// ─── SiteDetail ───────────────────────────────────────────────────────────────

export default function SiteDetail() {
  const { siteId } = useParams<{ siteId: string }>()
  const { sites, devices, stations: stationRegistry } = useRegistry()
  const { events } = useRealtimeEvents()

  const site = sites.find((s) => s.id === siteId)
  const siteDevices = devices.filter((d) => d.site_id === siteId)
  const siteStationStates = useDerivedStationState(events, siteId ?? null, stationRegistry)

  const today = format(new Date(), 'yyyy-MM-dd')

  // Last event received for this site
  const lastEventTs = useMemo(() => {
    const siteEvents = events.filter((e) => e.site_id === siteId)
    if (siteEvents.length === 0) return null
    return siteEvents.reduce((best, e) =>
      new Date(e.ts) > new Date(best.ts) ? e : best
    ).ts
  }, [events, siteId])

  // Event count today per device
  const eventCountByDevice = useMemo(() => {
    const counts = new Map<string, number>()
    for (const e of events) {
      if (e.site_id !== siteId) continue
      if (!isToday(parseISO(e.ts))) continue
      counts.set(e.device_id, (counts.get(e.device_id) ?? 0) + 1)
    }
    return counts
  }, [events, siteId])

  if (sites.length > 0 && !site) return <Navigate to="/sites" replace />

  // While registry is still loading, render a skeleton header
  if (!site) {
    return (
      <div className="p-6">
        <div className="h-7 w-48 rounded bg-surface-container-high animate-pulse" />
      </div>
    )
  }

  const siteEvents = events.filter((e) => e.site_id === siteId)

  return (
    <div className="flex flex-col gap-8 p-6 max-w-6xl">
      {/* ── Header ── */}
      <div className="flex flex-col gap-3">
        {/* Top row */}
        <div className="flex items-center gap-3 flex-wrap">
          <Link
            to="/sites"
            className="flex items-center gap-1 text-sm text-on-surface-variant hover:text-on-surface transition-colors"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
            All sites
          </Link>

          <h1 className="text-2xl font-bold text-on-surface">{site.name}</h1>

          {/* Edit — disabled placeholder */}
          <button
            disabled
            title="Editing not available yet"
            className="p-1.5 rounded-lg text-on-surface-variant opacity-40 cursor-not-allowed"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
            </svg>
          </button>

          <div className="ml-auto flex items-center gap-3">
            {/* Last-data chip */}
            <span className="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs
              bg-surface-container-high border border-outline-variant text-on-surface-variant">
              <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              {lastEventTs
                ? `Last data ${formatDistanceToNow(parseISO(lastEventTs), { addSuffix: true })}`
                : 'No data yet'}
            </span>

            <PDFExportButton events={siteEvents} siteId={site.id} date={today} />
          </div>
        </div>

        {/* Address / timezone */}
        {(site.address || site.timezone) && (
          <p className="text-sm text-on-surface-variant">
            {[site.address, site.timezone].filter(Boolean).join(' · ')}
          </p>
        )}
      </div>

      {/* ── Devices ── */}
      <section>
        <h2 className="text-sm font-semibold uppercase tracking-wider text-on-surface-variant mb-3">
          Devices
          <span className="ml-2 font-normal normal-case tracking-normal">({siteDevices.length})</span>
        </h2>
        {siteDevices.length === 0 ? (
          <p className="text-sm text-on-surface-variant">No devices registered for this site.</p>
        ) : (
          <div className="grid grid-cols-1 xl:grid-cols-2 2xl:grid-cols-3 gap-4">
            {siteDevices.map((device) => (
              <DeviceCard
                key={device.id}
                device={device}
                eventCountToday={eventCountByDevice.get(device.id) ?? 0}
                siteId={site.id}
              />
            ))}
          </div>
        )}
      </section>

      {/* ── Stations ── */}
      <section>
        <h2 className="text-sm font-semibold uppercase tracking-wider text-on-surface-variant mb-3">
          Stations
          <span className="ml-2 font-normal normal-case tracking-normal">({siteStationStates.length})</span>
        </h2>
        {siteStationStates.length === 0 ? (
          <p className="text-sm text-on-surface-variant">No station data for this site yet.</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4">
            {siteStationStates.map((state) => (
              <NavLink
                key={`${state.site_id}::${state.station}`}
                to={`/sites/${site.id}/stations/${state.station}`}
                className="block rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                <TempCard state={state} />
              </NavLink>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
