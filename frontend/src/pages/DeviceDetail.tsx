import { useMemo } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { format, formatDistanceToNow, isToday, parseISO } from 'date-fns'
import clsx from 'clsx'
import { useRegistry } from '../hooks/useRegistry'
import { useRealtimeEvents } from '../hooks/useRealtimeEvents'
import type { Device } from '../hooks/useRegistry'
import type { FoodSafetyEvent } from '../types'

// ─── shared helpers ───────────────────────────────────────────────────────────

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
  stale:  'bg-tertiary/20 text-tertiary',
  offline:'bg-error-container text-error',
}

const KIND_LABEL: Record<Device['kind'], string> = {
  kiosk:      '📱 Kiosk',
  'probe-sim':'🔌 Probe-sim',
}

const TYPE_BADGE: Record<string, string> = {
  temp:              'bg-surface-container-high text-on-surface-variant',
  alert:             'bg-error-container text-error',
  timer:             'bg-primary/10 text-primary',
  corrective_action: 'bg-primary/10 text-primary',
}

const TYPE_LABEL: Record<string, string> = {
  temp:              'Temp',
  alert:             'Alert',
  timer:             'Timer',
  corrective_action: 'CA',
}

function eventValue(e: FoodSafetyEvent): string {
  const p = e.payload as Record<string, unknown>
  switch (e.type) {
    case 'temp':   return `${Number(e.value).toFixed(1)}°F`
    case 'alert':  return `${Number(e.value).toFixed(1)}°F (lim ${p.threshold}°F)`
    case 'timer': {
      const ev = String(p.event ?? '')
      return ev === 'start' ? `${p.batch_label ?? ''} · ${p.hold_minutes ?? '?'} min` : `${p.batch_label ?? ''} ${ev}`
    }
    case 'corrective_action': return String(p.action_taken ?? 'logged')
    default: return ''
  }
}

// ─── KPI tile ─────────────────────────────────────────────────────────────────

function Kpi({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="flex flex-col gap-1 p-4 rounded-xl bg-surface-container border border-outline-variant">
      <p className="text-xs text-on-surface-variant">{label}</p>
      <p className="font-mono text-3xl font-bold text-on-surface tabular-nums leading-none">{value}</p>
      {sub && <p className="text-xs text-on-surface-variant">{sub}</p>}
    </div>
  )
}

// ─── DeviceDetail ─────────────────────────────────────────────────────────────

export default function DeviceDetail() {
  const { siteId, deviceId } = useParams<{ siteId: string; deviceId: string }>()
  const { sites, devices, stations: stationRegistry } = useRegistry()
  const { events } = useRealtimeEvents()

  const site   = sites.find((s) => s.id === siteId)
  const device = devices.find((d) => d.id === deviceId)

  const deviceEvents = useMemo(
    () => events.filter((e) => e.device_id === deviceId),
    [events, deviceId]
  )

  // ── KPIs ──────────────────────────────────────────────────────────────────

  const eventsToday = useMemo(
    () => deviceEvents.filter((e) => isToday(parseISO(e.ts))).length,
    [deviceEvents]
  )

  const alertsToday = useMemo(
    () => deviceEvents.filter((e) => e.type === 'alert' && isToday(parseISO(e.ts))).length,
    [deviceEvents]
  )

  const lastSeenLabel = useMemo(() => {
    if (!device?.last_seen) return '—'
    return formatDistanceToNow(parseISO(device.last_seen), { addSuffix: true })
  }, [device?.last_seen])

  const avgIntervalSec = useMemo(() => {
    const sorted = [...deviceEvents]
      .sort((a, b) => new Date(a.ts).getTime() - new Date(b.ts).getTime())
    if (sorted.length < 2) return null
    let sum = 0
    for (let i = 1; i < sorted.length; i++) {
      sum += new Date(sorted[i].ts).getTime() - new Date(sorted[i - 1].ts).getTime()
    }
    return ((sum / (sorted.length - 1)) / 1000).toFixed(1)
  }, [deviceEvents])

  // ── Stations posting via this device ─────────────────────────────────────

  const stationRows = useMemo(() => {
    const bySlug = new Map<string, FoodSafetyEvent[]>()
    for (const e of deviceEvents) {
      if (!bySlug.has(e.station)) bySlug.set(e.station, [])
      bySlug.get(e.station)!.push(e)
    }

    return Array.from(bySlug.entries())
      .map(([slug, evts]) => {
        const sorted = [...evts].sort(
          (a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime()
        )
        const lastTemp = sorted.find((e) => e.type === 'temp')
        const lastEvent = sorted[0]
        const record = stationRegistry.find((s) => s.site_id === siteId && s.slug === slug)
        const name = record?.name ?? slug.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
        return { slug, name, lastTemp: lastTemp ? Number(lastTemp.value) : null, lastEventTs: lastEvent.ts }
      })
      .sort((a, b) => a.name.localeCompare(b.name))
  }, [deviceEvents, stationRegistry, siteId])

  // ── Recent events ─────────────────────────────────────────────────────────

  const recentEvents = useMemo(
    () =>
      [...deviceEvents]
        .sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime())
        .slice(0, 30),
    [deviceEvents]
  )

  // ── Guards ────────────────────────────────────────────────────────────────

  if (devices.length > 0 && !device) return <Navigate to={`/sites/${siteId}`} replace />

  if (!device) {
    return (
      <div className="p-6">
        <div className="h-7 w-48 rounded bg-surface-container-high animate-pulse" />
      </div>
    )
  }

  const ds = deviceStatus(device.last_seen)

  return (
    <div className="flex flex-col gap-6 p-6 max-w-5xl">

      {/* ── Header ── */}
      <div className="flex items-center gap-3 flex-wrap">
        <Link
          to={`/sites/${siteId}`}
          className="flex items-center gap-1 text-sm text-on-surface-variant hover:text-on-surface transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          {site?.name ?? 'Site'}
        </Link>

        <h1 className="text-2xl font-bold text-on-surface">{device.name}</h1>

        <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-container-high text-on-surface-variant border border-outline-variant">
          {KIND_LABEL[device.kind]}
        </span>

        <span className={clsx('px-2.5 py-0.5 rounded-full text-xs font-semibold', STATUS_PILL[ds])}>
          {ds}
        </span>
      </div>

      {/* ── KPI strip ── */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-3">
        <Kpi label="Events today"      value={String(eventsToday)} />
        <Kpi label="Alerts today"      value={String(alertsToday)} />
        <Kpi label="Last seen"         value={lastSeenLabel} />
        <Kpi
          label="Avg post interval"
          value={avgIntervalSec !== null ? `${avgIntervalSec}s` : '—'}
          sub={avgIntervalSec !== null ? 'between events' : undefined}
        />
      </div>

      {/* ── Stations ── */}
      <div className="rounded-2xl border border-outline-variant bg-surface-container overflow-hidden">
        <div className="px-5 py-3 border-b border-outline-variant">
          <h2 className="text-sm font-semibold text-on-surface">
            Stations
            <span className="ml-2 text-on-surface-variant font-normal">({stationRows.length})</span>
          </h2>
        </div>

        {stationRows.length === 0 ? (
          <p className="px-5 py-4 text-sm text-on-surface-variant">No station data from this device yet.</p>
        ) : (
          <div className="divide-y divide-outline-variant/50">
            {stationRows.map((row) => (
              <div key={row.slug} className="flex items-center gap-3 px-5 py-3">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-on-surface truncate">{row.name}</p>
                  <p className="text-xs text-on-surface-variant mt-0.5">
                    {formatDistanceToNow(parseISO(row.lastEventTs), { addSuffix: true })}
                  </p>
                </div>

                {row.lastTemp !== null && (
                  <span className="font-mono text-sm font-semibold text-on-surface tabular-nums shrink-0">
                    {row.lastTemp.toFixed(1)}°F
                  </span>
                )}

                <Link
                  to={`/sites/${siteId}/stations/${row.slug}`}
                  className="shrink-0 flex items-center gap-0.5 text-xs font-semibold text-primary hover:text-primary/70 transition-colors"
                >
                  Open
                  <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                  </svg>
                </Link>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── Recent events ── */}
      <div className="rounded-2xl border border-outline-variant bg-surface-container overflow-hidden">
        <div className="px-5 py-3 border-b border-outline-variant">
          <h2 className="text-sm font-semibold text-on-surface">
            Recent events
            <span className="ml-2 text-on-surface-variant font-normal">({recentEvents.length})</span>
          </h2>
        </div>

        {recentEvents.length === 0 ? (
          <p className="px-5 py-4 text-sm text-on-surface-variant">No events from this device yet.</p>
        ) : (
          <div className="divide-y divide-outline-variant/50">
            {recentEvents.map((e) => (
              <div key={e.id} className="flex items-baseline gap-3 px-5 py-2 text-xs">
                <span className="shrink-0 font-mono text-on-surface-variant w-11">
                  {format(parseISO(e.ts), 'HH:mm')}
                </span>

                <span className={clsx(
                  'shrink-0 px-1.5 py-0.5 rounded text-[10px] font-semibold leading-none',
                  TYPE_BADGE[e.type] ?? 'bg-surface-variant text-on-surface-variant'
                )}>
                  {TYPE_LABEL[e.type] ?? e.type}
                </span>

                <span className="shrink-0 text-on-surface-variant w-32 truncate">
                  {e.station.replace(/-/g, ' ')}
                </span>

                <span className="text-on-surface truncate">{eventValue(e)}</span>
              </div>
            ))}
          </div>
        )}
      </div>

    </div>
  )
}
