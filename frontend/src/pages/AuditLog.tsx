import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { format, parseISO, startOfDay, endOfDay, subDays, subHours } from 'date-fns'
import clsx from 'clsx'
import { supabase } from '../services/supabase'
import { useRealtimeEvents } from '../hooks/useRealtimeEvents'
import { useRegistry } from '../hooks/useRegistry'
import AlertDetail from '../components/alerts/AlertDetail'
import type { FoodSafetyEvent } from '../types'

// ─── types ────────────────────────────────────────────────────────────────────

type EventType = 'temp' | 'alert' | 'timer' | 'corrective_action'
type DateRange = 'last24h' | 'today' | 'yesterday' | 'custom'

interface Filters {
  siteId: string
  deviceId: string
  station: string
  type: EventType | 'all'
  range: DateRange
  customFrom: string  // yyyy-MM-dd
  customTo: string
  search: string
}

const DEFAULT_FILTERS: Filters = {
  siteId: '',
  deviceId: '',
  station: '',
  type: 'all',
  range: 'last24h',
  customFrom: format(new Date(), 'yyyy-MM-dd'),
  customTo: format(new Date(), 'yyyy-MM-dd'),
  search: '',
}

// ─── date range → [from, to] ISO strings ──────────────────────────────────────

function resolveRange(f: Filters): { from: string; to: string } {
  const now = new Date()
  switch (f.range) {
    case 'last24h':
      return { from: subHours(now, 24).toISOString(), to: now.toISOString() }
    case 'today':
      return { from: startOfDay(now).toISOString(), to: endOfDay(now).toISOString() }
    case 'yesterday': {
      const y = subDays(now, 1)
      return { from: startOfDay(y).toISOString(), to: endOfDay(y).toISOString() }
    }
    case 'custom':
      return {
        from: startOfDay(parseISO(f.customFrom)).toISOString(),
        to: endOfDay(parseISO(f.customTo)).toISOString(),
      }
  }
}

// cache key so we don't re-fetch the same window twice
function cacheKey(from: string, to: string) {
  return `${from}|${to}`
}

// ─── payload formatting ───────────────────────────────────────────────────────

function valueLabel(e: FoodSafetyEvent): string {
  if (e.type === 'temp' || e.type === 'alert') return `${Number(e.value).toFixed(1)}°F`
  if (e.type === 'timer') {
    const p = e.payload as Record<string, unknown>
    return String(p.event ?? '')
  }
  return '—'
}

function payloadPreview(e: FoodSafetyEvent): string {
  const p = e.payload as Record<string, unknown>
  if (e.type === 'alert') return `threshold ${p.threshold}°F`
  if (e.type === 'timer') return `${p.batch_label ?? ''} · ${p.hold_minutes ?? '?'} min`
  if (e.type === 'corrective_action') return String(p.action_taken ?? '').slice(0, 60)
  return ''
}

// ─── type chip ────────────────────────────────────────────────────────────────

const TYPE_OPTIONS: Array<{ value: EventType | 'all'; label: string }> = [
  { value: 'all', label: 'All' },
  { value: 'temp', label: 'Temp' },
  { value: 'alert', label: 'Alert' },
  { value: 'timer', label: 'Timer' },
  { value: 'corrective_action', label: 'CA' },
]

const TYPE_BADGE: Record<string, string> = {
  temp:              'bg-surface-container-high text-on-surface-variant',
  alert:             'bg-error-container text-error',
  timer:             'bg-primary/10 text-primary',
  corrective_action: 'bg-primary/10 text-primary',
}

const RANGE_OPTIONS: Array<{ value: DateRange; label: string }> = [
  { value: 'last24h', label: 'Last 24h' },
  { value: 'today', label: 'Today' },
  { value: 'yesterday', label: 'Yesterday' },
  { value: 'custom', label: 'Custom' },
]

// ─── filter reducer ───────────────────────────────────────────────────────────

function filtersReducer(state: Filters, patch: Partial<Filters>): Filters {
  return { ...state, ...patch }
}

// ─── virtualized table ────────────────────────────────────────────────────────

const ROW_HEIGHT = 40
const OVERSCAN = 10

interface VirtualTableProps {
  rows: FoodSafetyEvent[]
  sites: Record<string, string>   // id → name
  devices: Record<string, string> // id → name
  expandedId: string | null
  onToggle: (e: FoodSafetyEvent) => void
}

function VirtualTable({ rows, sites, devices, expandedId, onToggle }: VirtualTableProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [scrollTop, setScrollTop] = useState(0)
  const [height, setHeight] = useState(480)

  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    const ro = new ResizeObserver(() => setHeight(el.clientHeight))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const startIdx = Math.max(0, Math.floor(scrollTop / ROW_HEIGHT) - OVERSCAN)
  const visible  = Math.ceil(height / ROW_HEIGHT) + OVERSCAN * 2
  const endIdx   = Math.min(rows.length, startIdx + visible)
  const totalH   = rows.length * ROW_HEIGHT

  return (
    <div
      ref={containerRef}
      className="flex-1 overflow-auto"
      onScroll={(e) => setScrollTop((e.target as HTMLElement).scrollTop)}
    >
      <div style={{ height: totalH, position: 'relative' }}>
        {rows.slice(startIdx, endIdx).map((e, i) => (
          <TableRow
            key={e.id}
            event={e}
            siteName={sites[e.site_id] ?? e.site_id}
            deviceName={devices[e.device_id] ?? e.device_id}
            expanded={expandedId === e.id}
            onToggle={onToggle}
            style={{ position: 'absolute', top: (startIdx + i) * ROW_HEIGHT, left: 0, right: 0 }}
          />
        ))}
      </div>
    </div>
  )
}

// ─── table row ────────────────────────────────────────────────────────────────

interface RowProps {
  event: FoodSafetyEvent
  siteName: string
  deviceName: string
  expanded: boolean
  onToggle: (e: FoodSafetyEvent) => void
  style?: React.CSSProperties
}

function TableRow({ event: e, siteName, deviceName, expanded, onToggle, style }: RowProps) {
  return (
    <div style={style}>
      <div
        onClick={() => onToggle(e)}
        className={clsx(
          'grid items-center gap-2 px-4 text-xs cursor-pointer border-b border-outline-variant/50 transition-colors',
          'hover:bg-surface-container-high',
          expanded && 'bg-surface-container-high',
          e.type === 'alert' && 'bg-error-container/10 hover:bg-error-container/20',
        )}
        style={{ height: ROW_HEIGHT, gridTemplateColumns: '7rem 1fr 1fr 1fr 4.5rem 5rem 1fr' }}
      >
        <span className="font-mono text-on-surface-variant tabular-nums shrink-0">
          {format(parseISO(e.ts), 'HH:mm:ss')}
        </span>
        <span className="truncate text-on-surface-variant">{siteName}</span>
        <span className="truncate text-on-surface-variant">{deviceName}</span>
        <span className="truncate text-on-surface">{e.station.replace(/-/g, ' ')}</span>
        <span className={clsx('px-1.5 py-0.5 rounded text-[10px] font-semibold leading-none w-fit', TYPE_BADGE[e.type] ?? '')}>
          {e.type === 'corrective_action' ? 'CA' : e.type}
        </span>
        <span className="font-mono tabular-nums text-on-surface">{valueLabel(e)}</span>
        <span className="truncate text-on-surface-variant">{payloadPreview(e)}</span>
      </div>

      {/* Expanded payload */}
      {expanded && e.type !== 'alert' && (
        <div className="px-4 py-3 bg-surface-container-high border-b border-outline-variant text-xs font-mono text-on-surface-variant whitespace-pre-wrap break-all">
          {JSON.stringify(e.payload, null, 2)}
        </div>
      )}
    </div>
  )
}

// ─── plain (non-virtual) table when rows ≤ 200 ───────────────────────────────

function PlainTable({ rows, sites, devices, expandedId, onToggle }: VirtualTableProps) {
  return (
    <div className="flex-1 overflow-auto divide-y divide-outline-variant/50">
      {rows.map((e) => (
        <TableRow
          key={e.id}
          event={e}
          siteName={sites[e.site_id] ?? e.site_id}
          deviceName={devices[e.device_id] ?? e.device_id}
          expanded={expandedId === e.id}
          onToggle={onToggle}
        />
      ))}
    </div>
  )
}

// ─── AuditLog ─────────────────────────────────────────────────────────────────

export default function AuditLog() {
  const { events: liveEvents } = useRealtimeEvents()
  const { sites, devices } = useRegistry()

  const [filters, dispatch] = useReducer(filtersReducer, DEFAULT_FILTERS)
  const [fetchedCache, setFetchedCache] = useState<Map<string, FoodSafetyEvent[]>>(new Map())
  const [fetching, setFetching] = useState(false)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [alertModal, setAlertModal] = useState<FoodSafetyEvent | null>(null)

  // ── build site/device label maps ────────────────────────────────────────────
  const siteMap = useMemo(
    () => Object.fromEntries(sites.map((s) => [s.id, s.name])),
    [sites]
  )
  const deviceMap = useMemo(
    () => Object.fromEntries(devices.map((d) => [d.id, d.name])),
    [devices]
  )

  // unique station slugs from live events (for dropdown)
  const allStations = useMemo(() => {
    const s = new Set<string>()
    for (const e of liveEvents) s.add(e.station)
    return Array.from(s).sort()
  }, [liveEvents])

  // ── decide if we need to fetch from DB ──────────────────────────────────────
  const { from, to } = useMemo(() => resolveRange(filters), [filters])
  const key = cacheKey(from, to)

  // live cache covers newest MAX_EVENTS — check if from is within it
  const liveCoversRange = useMemo(() => {
    if (liveEvents.length === 0) return true
    const oldest = liveEvents.reduce(
      (min, e) => (e.ts < min ? e.ts : min), liveEvents[0].ts
    )
    return from >= oldest
  }, [liveEvents, from])

  useEffect(() => {
    if (liveCoversRange) return
    if (fetchedCache.has(key)) return

    setFetching(true)
    supabase
      .from('events')
      .select('*')
      .gte('ts', from)
      .lt('ts', to)
      .order('ts', { ascending: false })
      .limit(2000)
      .then(({ data }) => {
        setFetchedCache((prev) => {
          const next = new Map(prev)
          next.set(key, (data as FoodSafetyEvent[]) ?? [])
          return next
        })
        setFetching(false)
      }, () => {
        setFetching(false)
      })
  }, [key, liveCoversRange])

  // ── merge live + fetched, dedupe by id ──────────────────────────────────────
  const baseEvents = useMemo(() => {
    const fetched = fetchedCache.get(key) ?? []
    if (liveCoversRange) return liveEvents
    const seen = new Set<string>()
    const merged: FoodSafetyEvent[] = []
    for (const e of [...liveEvents, ...fetched]) {
      if (!seen.has(e.id)) { seen.add(e.id); merged.push(e) }
    }
    return merged
  }, [liveEvents, fetchedCache, key, liveCoversRange])

  // ── apply filters ────────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    const searchLower = filters.search.toLowerCase()
    return baseEvents
      .filter((e) => {
        if (e.ts < from || e.ts > to) return false
        if (filters.siteId && e.site_id !== filters.siteId) return false
        if (filters.deviceId && e.device_id !== filters.deviceId) return false
        if (filters.station && e.station !== filters.station) return false
        if (filters.type !== 'all' && e.type !== filters.type) return false
        if (searchLower) {
          const hay = JSON.stringify(e.payload).toLowerCase() +
            e.station.toLowerCase() + e.type
          if (!hay.includes(searchLower)) return false
        }
        return true
      })
      .sort((a, b) => b.ts.localeCompare(a.ts))
  }, [baseEvents, filters, from, to])

  // ── row toggle ───────────────────────────────────────────────────────────────
  function handleRowToggle(e: FoodSafetyEvent) {
    if (e.type === 'alert') {
      setAlertModal(e)
      return
    }
    setExpandedId((prev) => (prev === e.id ? null : e.id))
  }

  // ── filter helpers ───────────────────────────────────────────────────────────
  const f = filters

  function selectClass(hasValue: boolean) {
    return clsx(
      'px-3 py-1.5 rounded-lg text-sm border transition-colors focus:outline-none focus:border-primary cursor-pointer',
      'bg-surface-container-high text-on-surface',
      hasValue ? 'border-primary' : 'border-outline-variant'
    )
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">

      {/* ── Filters ── */}
      <div className="shrink-0 px-5 py-3 border-b border-outline-variant bg-surface-container flex flex-col gap-2">
        {/* Row 1: dropdowns + search */}
        <div className="flex items-center gap-2 flex-wrap">
          {/* Site */}
          <select
            value={f.siteId}
            onChange={(e) => dispatch({ siteId: e.target.value })}
            className={selectClass(!!f.siteId)}
          >
            <option value="">All sites</option>
            {sites.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>

          {/* Device */}
          <select
            value={f.deviceId}
            onChange={(e) => dispatch({ deviceId: e.target.value })}
            className={selectClass(!!f.deviceId)}
          >
            <option value="">All devices</option>
            {devices
              .filter((d) => !f.siteId || d.site_id === f.siteId)
              .map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
          </select>

          {/* Station */}
          <select
            value={f.station}
            onChange={(e) => dispatch({ station: e.target.value })}
            className={selectClass(!!f.station)}
          >
            <option value="">All stations</option>
            {allStations.map((s) => (
              <option key={s} value={s}>{s.replace(/-/g, ' ')}</option>
            ))}
          </select>

          {/* Search */}
          <div className="relative ml-auto">
            <svg className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-on-surface-variant" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="search"
              placeholder="Search payload…"
              value={f.search}
              onChange={(e) => dispatch({ search: e.target.value })}
              className="pl-8 pr-3 py-1.5 rounded-lg text-sm bg-surface-container-high border border-outline-variant
                text-on-surface placeholder:text-on-surface-variant focus:outline-none focus:border-primary w-52"
            />
          </div>
        </div>

        {/* Row 2: type chips + date range */}
        <div className="flex items-center gap-2 flex-wrap">
          {/* Type chips */}
          <div className="flex items-center gap-1">
            {TYPE_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                onClick={() => dispatch({ type: opt.value })}
                className={clsx(
                  'px-2.5 py-1 rounded-full text-xs font-semibold transition-colors',
                  f.type === opt.value
                    ? 'bg-primary text-on-primary'
                    : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface'
                )}
              >
                {opt.label}
              </button>
            ))}
          </div>

          {/* Date range */}
          <div className="flex items-center gap-1 ml-4">
            {RANGE_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                onClick={() => dispatch({ range: opt.value })}
                className={clsx(
                  'px-2.5 py-1 rounded-full text-xs font-semibold transition-colors',
                  f.range === opt.value
                    ? 'bg-primary text-on-primary'
                    : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface'
                )}
              >
                {opt.label}
              </button>
            ))}
          </div>

          {/* Custom date inputs */}
          {f.range === 'custom' && (
            <div className="flex items-center gap-1 ml-2">
              <input
                type="date"
                value={f.customFrom}
                onChange={(e) => dispatch({ customFrom: e.target.value })}
                className="px-2 py-1 rounded-lg text-xs bg-surface-container-high border border-outline-variant
                  text-on-surface focus:outline-none focus:border-primary cursor-pointer"
              />
              <span className="text-xs text-on-surface-variant">to</span>
              <input
                type="date"
                value={f.customTo}
                onChange={(e) => dispatch({ customTo: e.target.value })}
                className="px-2 py-1 rounded-lg text-xs bg-surface-container-high border border-outline-variant
                  text-on-surface focus:outline-none focus:border-primary cursor-pointer"
              />
            </div>
          )}

          {/* Row count + loading indicator */}
          <div className="ml-auto flex items-center gap-2">
            {fetching && (
              <div className="w-3.5 h-3.5 border-2 border-primary border-t-transparent rounded-full animate-spin" />
            )}
            <span className="text-xs text-on-surface-variant">
              {filtered.length.toLocaleString()} event{filtered.length !== 1 ? 's' : ''}
            </span>
          </div>
        </div>
      </div>

      {/* ── Table header ── */}
      <div
        className="shrink-0 grid gap-2 px-4 py-2 text-[10px] font-semibold uppercase tracking-wider
          text-on-surface-variant border-b border-outline-variant bg-surface-container"
        style={{ gridTemplateColumns: '7rem 1fr 1fr 1fr 4.5rem 5rem 1fr' }}
      >
        <span>Time</span>
        <span>Site</span>
        <span>Device</span>
        <span>Station</span>
        <span>Type</span>
        <span>Value</span>
        <span>Payload</span>
      </div>

      {/* ── Table body ── */}
      {filtered.length === 0 ? (
        <div className="flex-1 flex items-center justify-center text-sm text-on-surface-variant">
          No events match the current filters.
        </div>
      ) : filtered.length > 200 ? (
        <VirtualTable
          rows={filtered}
          sites={siteMap}
          devices={deviceMap}
          expandedId={expandedId}
          onToggle={handleRowToggle}
        />
      ) : (
        <PlainTable
          rows={filtered}
          sites={siteMap}
          devices={deviceMap}
          expandedId={expandedId}
          onToggle={handleRowToggle}
        />
      )}

      {/* Alert detail modal */}
      {alertModal && (
        <AlertDetail
          alert={alertModal}
          allEvents={liveEvents}
          onClose={() => setAlertModal(null)}
        />
      )}
    </div>
  )
}
