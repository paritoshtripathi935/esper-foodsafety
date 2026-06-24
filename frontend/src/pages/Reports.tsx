import { useMemo, useState, useEffect } from 'react'
import { useOutletContext } from 'react-router-dom'
import {
  format,
  parseISO,
  isWithinInterval,
  startOfDay,
  endOfDay,
  subDays,
  isToday,
  formatDistanceToNow,
} from 'date-fns'
import { pdf } from '@react-pdf/renderer'
import clsx from 'clsx'
import HACCPReport from '../components/pdf/HACCPReport'
import EmptyState from '../components/common/EmptyState'
import { uploadPdf, listPdfs, presignPdf } from '../services/ai'
import type { S3PdfFile } from '../services/ai'
import type { FoodSafetyEvent, Site, Station } from '../types'

// ─── outlet context ───────────────────────────────────────────────────────────

interface ShellContext {
  events: FoodSafetyEvent[]
  sites: Site[]
  stationRegistry: Station[]
  selectedSite: string | null
  setSelectedSite: (s: string | null) => void
  addToast: (msg: string) => void
  handleSeedDemo: () => Promise<void>
}

// ─── per-day export button (inline, not reusing PDFExportButton to allow
//     passing addToast from context) ──────────────────────────────────────────

interface ExportButtonProps {
  events: FoodSafetyEvent[]
  siteId: string
  date: string          // yyyy-MM-dd
  addToast: (msg: string) => void
  small?: boolean
  onUploaded?: () => void
}

function ExportButton({ events, siteId, date, addToast, small, onUploaded }: ExportButtonProps) {
  const [loading, setLoading] = useState(false)

  async function handleExport() {
    setLoading(true)
    try {
      const blob = await pdf(
        <HACCPReport events={events} siteId={siteId} date={date} />
      ).toBlob()

      // local download
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `haccp-${siteId}-${date}.pdf`
      a.click()
      URL.revokeObjectURL(url)

      // upload to S3 via EC2
      try {
        const result = await uploadPdf(blob, siteId, date)
        addToast(`Saved to S3: ${result.bucket}/${result.key}`)
        onUploaded?.()
      } catch (err) {
        addToast(`Downloaded locally (S3 upload failed: ${(err as Error).message})`)
      }
    } catch (err) {
      addToast(`PDF generation failed: ${(err as Error).message}`)
    } finally {
      setLoading(false)
    }
  }

  return (
    <button
      onClick={handleExport}
      disabled={loading}
      className={clsx(
        'flex items-center gap-1.5 rounded-lg font-semibold transition-colors',
        'bg-primary-container text-on-primary hover:bg-primary/80 disabled:opacity-60 disabled:cursor-not-allowed',
        small ? 'px-2.5 py-1 text-xs' : 'px-4 py-2 text-sm'
      )}
    >
      {loading ? (
        <div className={clsx('border-2 border-on-primary border-t-transparent rounded-full animate-spin', small ? 'w-3 h-3' : 'w-4 h-4')} />
      ) : (
        <svg className={small ? 'w-3 h-3' : 'w-4 h-4'} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
      )}
      {small ? 'PDF' : 'Export PDF'}
    </button>
  )
}

// ─── day card ─────────────────────────────────────────────────────────────────

interface DayCardProps {
  date: string  // yyyy-MM-dd
  dayEvents: FoodSafetyEvent[]
  siteId: string  // 'all' or a real site id
  addToast: (msg: string) => void
  onUploaded?: () => void
}

function DayCard({ date, dayEvents, siteId, addToast, onUploaded }: DayCardProps) {
  const [expanded, setExpanded] = useState(false)

  const alerts = dayEvents.filter((e) => e.type === 'alert')
  const cas    = dayEvents.filter((e) => e.type === 'corrective_action')
  const compliant = alerts.length === 0

  // group by station for expanded view
  const byStation = useMemo(() => {
    const map = new Map<string, FoodSafetyEvent[]>()
    for (const e of dayEvents) {
      if (!map.has(e.station)) map.set(e.station, [])
      map.get(e.station)!.push(e)
    }
    return Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b))
  }, [dayEvents])

  const dateLabel = format(parseISO(date), 'EEE, MMM d')
  const exportSiteId = siteId === 'all'
    ? (dayEvents[0]?.site_id ?? 'site-eastgate')
    : siteId

  return (
    <div className="bg-surface-container overflow-hidden">
      {/* Main row */}
      <div
        className="flex items-center gap-4 px-4 py-2.5 cursor-pointer hover:bg-surface-container-high transition-colors"
        onClick={() => setExpanded((v) => !v)}
      >
        {/* Chevron */}
        <svg
          className={clsx('w-4 h-4 shrink-0 text-on-surface-variant transition-transform', expanded && 'rotate-90')}
          fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>

        {/* Date */}
        <p className="font-semibold text-on-surface w-40 shrink-0">
          {dateLabel}
          {isToday(parseISO(date)) && (
            <span className="ml-2 text-[10px] font-semibold uppercase tracking-wider text-primary">Today</span>
          )}
        </p>

        {/* Stats */}
        <div className="flex items-center gap-4 text-xs text-on-surface-variant">
          <span>{dayEvents.length} events</span>
          <span>{alerts.length} alert{alerts.length !== 1 ? 's' : ''}</span>
          <span>{cas.length} CA{cas.length !== 1 ? 's' : ''}</span>
        </div>

        {/* Status pill */}
        <span className={clsx(
          'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold shrink-0',
          compliant ? 'bg-primary/10 text-primary' : 'bg-error-container text-error'
        )}>
          {compliant ? '✓ Compliant' : `⚠ ${alerts.length} breach${alerts.length !== 1 ? 'es' : ''}`}
        </span>

        <div className="ml-auto shrink-0" onClick={(e) => e.stopPropagation()}>
          <ExportButton
            events={dayEvents}
            siteId={exportSiteId}
            date={date}
            addToast={addToast}
            small
            onUploaded={onUploaded}
          />
        </div>
      </div>

      {/* Expanded: events by station */}
      {expanded && (
        <div className="border-t border-outline-variant divide-y divide-outline-variant/50">
          {byStation.map(([station, evts]) => {
            const sorted = [...evts].sort((a, b) => a.ts.localeCompare(b.ts))
            return (
              <div key={station} className="px-4 py-2">
                <p className="text-xs font-semibold text-on-surface-variant uppercase tracking-wide mb-1">
                  {station.replace(/-/g, ' ')}
                </p>
                <div className="flex flex-col gap-0.5">
                  {sorted.map((e) => (
                    <div key={e.id} className="flex items-baseline gap-2 text-xs">
                      <span className="font-mono text-on-surface-variant w-16 shrink-0">
                        {format(parseISO(e.ts), 'HH:mm:ss')}
                      </span>
                      <span className={clsx(
                        'px-1.5 py-0.5 rounded text-[10px] font-semibold leading-none shrink-0',
                        e.type === 'alert' ? 'bg-error-container text-error' :
                        e.type === 'corrective_action' ? 'bg-primary/10 text-primary' :
                        'bg-surface-container-high text-on-surface-variant'
                      )}>
                        {e.type === 'corrective_action' ? 'CA' : e.type}
                      </span>
                      <span className="text-on-surface truncate">
                        {e.type === 'temp' || e.type === 'alert'
                          ? `${Number(e.value).toFixed(1)}°F`
                          : String((e.payload as Record<string, unknown>).action_taken ?? e.type)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

// ─── Reports ─────────────────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes >= 1_048_576) return `${(bytes / 1_048_576).toFixed(1)} MB`
  return `${Math.round(bytes / 1024)} KB`
}

export default function Reports() {
  const { events, sites, selectedSite, addToast, handleSeedDemo } =
    useOutletContext<ShellContext>()

  const [siteFilter, setSiteFilter] = useState<string>(selectedSite ?? '')

  const [s3Files, setS3Files] = useState<S3PdfFile[]>([])
  const [s3Loading, setS3Loading] = useState(false)
  const [s3Error, setS3Error] = useState<string | null>(null)

  function refreshS3() {
    listPdfs(siteFilter || undefined).then(setS3Files).catch(() => {})
  }

  useEffect(() => {
    setS3Loading(true)
    setS3Error(null)
    listPdfs(siteFilter || undefined)
      .then(setS3Files)
      .catch((e) => setS3Error(e.message))
      .finally(() => setS3Loading(false))
  }, [siteFilter])

  const today = format(new Date(), 'yyyy-MM-dd')

  // filter events to selected site + last 30 days
  const windowStart = startOfDay(subDays(new Date(), 29))
  const windowEnd   = endOfDay(new Date())

  const windowEvents = useMemo(
    () =>
      events.filter((e) => {
        if (siteFilter && e.site_id !== siteFilter) return false
        const d = parseISO(e.ts)
        return isWithinInterval(d, { start: windowStart, end: windowEnd })
      }),
    [events, siteFilter, windowStart, windowEnd]
  )

  // group by date string yyyy-MM-dd, newest first
  const byDate = useMemo(() => {
    const map = new Map<string, FoodSafetyEvent[]>()
    for (const e of windowEvents) {
      const d = format(parseISO(e.ts), 'yyyy-MM-dd')
      if (!map.has(d)) map.set(d, [])
      map.get(d)!.push(e)
    }
    // sort dates descending
    return Array.from(map.entries()).sort(([a], [b]) => b.localeCompare(a))
  }, [windowEvents])

  const exportSiteId = siteFilter || (sites[0]?.id ?? 'site-eastgate')
  const todayEvents  = windowEvents.filter(
    (e) => format(parseISO(e.ts), 'yyyy-MM-dd') === today
  )

  return (
    <div className="flex flex-col gap-6 p-6 max-w-7xl">

      {/* ── Header strip ── */}
      <div className="flex items-center gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-bold text-on-surface">HACCP Reports</h1>
          <p className="text-sm text-on-surface-variant mt-0.5">Daily compliance log</p>
        </div>

        {/* Site selector */}
        <select
          value={siteFilter}
          onChange={(e) => setSiteFilter(e.target.value)}
          className="px-3 py-1.5 rounded-lg text-sm bg-surface-container-high border border-outline-variant
            text-on-surface focus:outline-none focus:border-primary cursor-pointer"
        >
          <option value="">All sites</option>
          {sites.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>

        {/* Refresh S3 list */}
        <button
          onClick={refreshS3}
          title="Refresh S3 file list"
          className="p-1.5 rounded-lg text-on-surface-variant border border-outline-variant
            hover:bg-surface-container-high transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>

        {/* CTA — today's report */}
        <div className="ml-auto">
          <ExportButton
            events={todayEvents}
            siteId={exportSiteId}
            date={today}
            addToast={addToast}
            onUploaded={refreshS3}
          />
        </div>
      </div>

      {/* ── Two-column body ── */}
      <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-6 items-start">

        {/* Column A: past-30-days list */}
        <section className="flex flex-col gap-2">
          <h2 className="text-sm font-semibold text-on-surface-variant uppercase tracking-wide">
            Last 30 days
          </h2>

          {byDate.length === 0 ? (
            <EmptyState
              title="No events in the last 30 days"
              body="Seed demo data to see compliance records."
              onSeed={handleSeedDemo}
            />
          ) : (
            <div className="flex flex-col divide-y divide-outline-variant/30 rounded-xl overflow-hidden border border-outline-variant">
              {byDate.map(([date, dayEvts]) => (
                <DayCard
                  key={date}
                  date={date}
                  dayEvents={dayEvts}
                  siteId={siteFilter || 'all'}
                  addToast={addToast}
                  onUploaded={refreshS3}
                />
              ))}
            </div>
          )}
        </section>

        {/* Column B: S3 file list */}
        <section className="flex flex-col gap-3">
          <h2 className="text-sm font-semibold text-on-surface-variant uppercase tracking-wide">
            Stored on S3
          </h2>

          {s3Loading && (
            <div className="rounded-xl border border-outline-variant bg-surface-container p-5 flex items-center justify-center gap-2 text-sm text-on-surface-variant">
              <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
              Loading…
            </div>
          )}

          {!s3Loading && s3Error && (
            <div className="rounded-xl border border-error/30 bg-error-container/20 p-4 text-sm text-error">
              Failed to load S3 files: {s3Error}
            </div>
          )}

          {!s3Loading && !s3Error && s3Files.length === 0 && (
            <div className="rounded-xl border border-outline-variant bg-surface-container p-5 text-center">
              <svg className="w-8 h-8 mx-auto text-on-surface-variant opacity-40 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
              </svg>
              <p className="text-sm text-on-surface-variant">No files yet.</p>
              <p className="text-xs text-on-surface-variant mt-1 opacity-70">
                Export a report to upload the first PDF.
              </p>
            </div>
          )}

          {!s3Loading && !s3Error && s3Files.length > 0 && (
            <div className="rounded-xl border border-outline-variant bg-surface-container divide-y divide-outline-variant/50 overflow-hidden">
              {s3Files.map((file) => (
                <div key={file.key} className="flex items-start gap-3 px-4 py-3 hover:bg-surface-container-high transition-colors">
                  <svg className="w-4 h-4 mt-0.5 shrink-0 text-on-surface-variant" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-on-surface truncate">
                      {file.date || file.key.split('/').pop()}
                      {file.site_id && (
                        <span className="ml-2 text-xs text-on-surface-variant font-normal">{file.site_id}</span>
                      )}
                    </p>
                    <p className="text-xs text-on-surface-variant mt-0.5">
                      {formatBytes(file.size)} · uploaded {formatDistanceToNow(parseISO(file.last_modified), { addSuffix: true })}
                    </p>
                  </div>
                  <button
                    className="shrink-0 px-2.5 py-1 rounded-lg text-xs font-semibold bg-primary-container text-on-primary hover:bg-primary/80 transition-colors"
                    onClick={() =>
                      presignPdf(file.key).then((url) => window.open(url, '_blank'))
                    }
                  >
                    Download
                  </button>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>

    </div>
  )
}
