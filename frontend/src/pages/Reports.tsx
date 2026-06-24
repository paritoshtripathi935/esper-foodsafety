import { useMemo, useState, useEffect } from "react";
import { useOutletContext } from "react-router-dom";
import {
  format,
  parseISO,
  isWithinInterval,
  startOfDay,
  endOfDay,
  subDays,
  isToday,
  formatDistanceToNow,
} from "date-fns";
import { pdf } from "@react-pdf/renderer";
import clsx from "clsx";
import HACCPReport from "../components/pdf/HACCPReport";
import EmptyState from "../components/common/EmptyState";
import { uploadPdf, listPdfs, presignPdf } from "../services/ai";
import type { S3PdfFile } from "../services/ai";
import type { FoodSafetyEvent, Site, Station } from "../types";

interface ShellContext {
  events: FoodSafetyEvent[];
  sites: Site[];
  stationRegistry: Station[];
  selectedSite: string | null;
  setSelectedSite: (s: string | null) => void;
  addToast: (msg: string) => void;
  handleSeedDemo: () => Promise<void>;
}

// ─── ExportButton ─────────────────────────────────────────────────────────────

interface ExportButtonProps {
  events: FoodSafetyEvent[];
  siteId: string;
  date: string;
  addToast: (msg: string) => void;
  size?: "sm" | "md" | "lg";
  onUploaded?: () => void;
}

function ExportButton({
  events,
  siteId,
  date,
  addToast,
  size = "md",
  onUploaded,
}: ExportButtonProps) {
  const [loading, setLoading] = useState(false);

  async function handleExport() {
    setLoading(true);
    try {
      const blob = await pdf(
        <HACCPReport events={events} siteId={siteId} date={date} />,
      ).toBlob();

      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `haccp-${siteId}-${date}.pdf`;
      a.click();
      URL.revokeObjectURL(url);

      try {
        const result = await uploadPdf(blob, siteId, date);
        addToast(`Saved to S3: ${result.bucket}/${result.key}`);
        onUploaded?.();
      } catch (err) {
        addToast(
          `Downloaded locally (S3 upload failed: ${(err as Error).message})`,
        );
      }
    } catch (err) {
      addToast(`PDF generation failed: ${(err as Error).message}`);
    } finally {
      setLoading(false);
    }
  }

  const sizeClasses = {
    sm: "px-2.5 py-1 text-xs",
    md: "px-4 py-2 text-sm",
    lg: "px-5 py-2.5 text-base font-semibold",
  };
  const iconSize =
    size === "sm" ? "w-3 h-3" : size === "lg" ? "w-5 h-5" : "w-4 h-4";
  const spinSize =
    size === "sm" ? "w-3 h-3" : size === "lg" ? "w-5 h-5" : "w-4 h-4";

  return (
    <button
      onClick={handleExport}
      disabled={loading}
      className={clsx(
        "flex items-center gap-2 rounded-lg font-semibold transition-colors",
        "bg-primary-container text-on-primary hover:bg-primary/80 disabled:opacity-60 disabled:cursor-not-allowed",
        sizeClasses[size],
      )}
    >
      {loading ? (
        <div
          className={clsx(
            "border-2 border-on-primary border-t-transparent rounded-full animate-spin",
            spinSize,
          )}
        />
      ) : (
        <svg
          className={iconSize}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
          />
        </svg>
      )}
      {size === "sm"
        ? "PDF"
        : size === "lg"
          ? `Export Today · ${format(parseISO(date), "MMM d")}`
          : "Export PDF"}
    </button>
  );
}

// ─── DayCard ──────────────────────────────────────────────────────────────────

interface DayCardProps {
  date: string;
  dayEvents: FoodSafetyEvent[];
  siteId: string;
  addToast: (msg: string) => void;
  onUploaded?: () => void;
}

function DayCard({
  date,
  dayEvents,
  siteId,
  addToast,
  onUploaded,
}: DayCardProps) {
  const [expanded, setExpanded] = useState(false);

  const alerts = dayEvents.filter((e) => e.type === "alert");
  const cas = dayEvents.filter((e) => e.type === "corrective_action");
  const compliant = alerts.length === 0;

  const byStation = useMemo(() => {
    const map = new Map<string, FoodSafetyEvent[]>();
    for (const e of dayEvents) {
      if (!map.has(e.station)) map.set(e.station, []);
      map.get(e.station)!.push(e);
    }
    return Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b));
  }, [dayEvents]);

  const dateLabel = format(parseISO(date), "EEE, MMM d");
  const exportSiteId =
    siteId === "all" ? (dayEvents[0]?.site_id ?? "site-eastgate") : siteId;

  return (
    <div>
      <div
        className="flex items-center gap-4 px-4 py-2.5 cursor-pointer hover:bg-surface-container-high transition-colors"
        onClick={() => setExpanded((v) => !v)}
      >
        <svg
          className={clsx(
            "w-4 h-4 shrink-0 text-on-surface-variant transition-transform",
            expanded && "rotate-90",
          )}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2.5}
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>

        <p className="font-semibold text-on-surface w-40 shrink-0">
          {dateLabel}
          {isToday(parseISO(date)) && (
            <span className="ml-2 text-[10px] font-semibold uppercase tracking-wider text-primary">
              Today
            </span>
          )}
        </p>

        <div className="flex items-center gap-4 text-xs text-on-surface-variant">
          <span>{dayEvents.length} events</span>
          <span>
            {alerts.length} alert{alerts.length !== 1 ? "s" : ""}
          </span>
          <span>
            {cas.length} CA{cas.length !== 1 ? "s" : ""}
          </span>
        </div>

        <span
          className={clsx(
            "ml-2 px-2 py-0.5 rounded-full text-xs font-semibold shrink-0",
            compliant
              ? "bg-primary/10 text-primary"
              : "bg-error-container text-error",
          )}
        >
          {compliant
            ? "✓ Compliant"
            : `⚠ ${alerts.length} breach${alerts.length !== 1 ? "es" : ""}`}
        </span>

        <div className="ml-auto shrink-0" onClick={(e) => e.stopPropagation()}>
          <ExportButton
            events={dayEvents}
            siteId={exportSiteId}
            date={date}
            addToast={addToast}
            size="sm"
            onUploaded={onUploaded}
          />
        </div>
      </div>

      {expanded && (
        <div className="border-t border-outline-variant divide-y divide-outline-variant/50">
          {byStation.map(([station, evts]) => {
            const sorted = [...evts].sort((a, b) => a.ts.localeCompare(b.ts));
            return (
              <div key={station} className="px-4 py-2">
                <p className="text-xs font-semibold text-on-surface-variant uppercase tracking-wide mb-1">
                  {station.replace(/-/g, " ")}
                </p>
                <div className="flex flex-col gap-0.5">
                  {sorted.map((e) => (
                    <div
                      key={e.id}
                      className="flex items-baseline gap-2 text-xs"
                    >
                      <span className="font-mono text-on-surface-variant w-16 shrink-0">
                        {format(parseISO(e.ts), "HH:mm:ss")}
                      </span>
                      <span
                        className={clsx(
                          "px-1.5 py-0.5 rounded text-[10px] font-semibold leading-none shrink-0",
                          e.type === "alert"
                            ? "bg-error-container text-error"
                            : e.type === "corrective_action"
                              ? "bg-primary/10 text-primary"
                              : "bg-surface-container-high text-on-surface-variant",
                        )}
                      >
                        {e.type === "corrective_action" ? "CA" : e.type}
                      </span>
                      <span className="text-on-surface truncate">
                        {e.type === "temp" || e.type === "alert"
                          ? `${Number(e.value).toFixed(1)}°F`
                          : String(
                              (e.payload as Record<string, unknown>)
                                .action_taken ?? e.type,
                            )}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ─── formatBytes ──────────────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes >= 1_048_576) return `${(bytes / 1_048_576).toFixed(1)} MB`;
  return `${Math.round(bytes / 1024)} KB`;
}

// ─── Reports ──────────────────────────────────────────────────────────────────

export default function Reports() {
  const { events, sites, selectedSite, addToast, handleSeedDemo } =
    useOutletContext<ShellContext>();

  const [siteFilter, setSiteFilter] = useState<string>(selectedSite ?? "");
  const [s3Files, setS3Files] = useState<S3PdfFile[]>([]);
  const [s3Loading, setS3Loading] = useState(false);
  const [s3Error, setS3Error] = useState<string | null>(null);

  function refreshS3() {
    listPdfs(siteFilter || undefined)
      .then(setS3Files)
      .catch(() => {});
  }

  useEffect(() => {
    setS3Loading(true);
    setS3Error(null);
    listPdfs(siteFilter || undefined)
      .then(setS3Files)
      .catch((e) => setS3Error(e.message))
      .finally(() => setS3Loading(false));
  }, [siteFilter]);

  const today = format(new Date(), "yyyy-MM-dd");
  const windowStart = startOfDay(subDays(new Date(), 29));
  const windowEnd = endOfDay(new Date());

  const windowEvents = useMemo(
    () =>
      events.filter((e) => {
        if (siteFilter && e.site_id !== siteFilter) return false;
        const d = parseISO(e.ts);
        return isWithinInterval(d, { start: windowStart, end: windowEnd });
      }),
    [events, siteFilter, windowStart, windowEnd],
  );

  const byDate = useMemo(() => {
    const map = new Map<string, FoodSafetyEvent[]>();
    for (const e of windowEvents) {
      const d = format(parseISO(e.ts), "yyyy-MM-dd");
      if (!map.has(d)) map.set(d, []);
      map.get(d)!.push(e);
    }
    return Array.from(map.entries()).sort(([a], [b]) => b.localeCompare(a));
  }, [windowEvents]);

  // ── compliance stats ────────────────────────────────────────────────────────
  const stats = useMemo(() => {
    const alerts30 = windowEvents.filter((e) => e.type === "alert").length;
    const cas30 = windowEvents.filter(
      (e) => e.type === "corrective_action",
    ).length;
    const activeDays = byDate.length;
    const compliance = (
      100 -
      (alerts30 / Math.max(windowEvents.length, 1)) * 100
    ).toFixed(1);

    const last7Start = startOfDay(subDays(new Date(), 6));
    const prior7Start = startOfDay(subDays(new Date(), 13));
    const prior7End = endOfDay(subDays(new Date(), 6));

    const alerts7 = windowEvents.filter(
      (e) => e.type === "alert" && parseISO(e.ts) >= last7Start,
    ).length;
    const alertsPrior7 = events.filter((e) => {
      if (siteFilter && e.site_id !== siteFilter) return false;
      const d = parseISO(e.ts);
      return e.type === "alert" && d >= prior7Start && d <= prior7End;
    }).length;

    const trendDelta = alertsPrior7 - alerts7;
    let trend: { label: string; up: boolean } | null = null;
    if (alertsPrior7 > alerts7) {
      trend = {
        label: `↑ ${trendDelta} fewer breach${trendDelta !== 1 ? "es" : ""} than last 7 days`,
        up: true,
      };
    } else if (alerts7 > alertsPrior7) {
      const n = alerts7 - alertsPrior7;
      trend = {
        label: `↓ ${n} more breach${n !== 1 ? "es" : ""} than last 7 days`,
        up: false,
      };
    }

    return { alerts30, cas30, activeDays, compliance, trend };
  }, [windowEvents, byDate, events, siteFilter]);

  const exportSiteId = siteFilter || (sites[0]?.id ?? "site-eastgate");
  const todayEvents = windowEvents.filter(
    (e) => format(parseISO(e.ts), "yyyy-MM-dd") === today,
  );

  return (
    <div className="flex flex-col gap-6 p-6 pt-4 max-w-[1400px]">
      {/* ── Header strip ── */}
      <div className="sticky top-0 z-10 bg-surface-container/95 backdrop-blur border-b border-outline-variant -mx-6 px-6 py-4">
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <div>
            <h1 className="text-xl font-bold text-on-surface">HACCP Reports</h1>
            <p className="text-sm text-on-surface-variant mt-0.5">
              Daily compliance log
            </p>
          </div>

          <div className="flex items-center gap-2">
            <select
              value={siteFilter}
              onChange={(e) => setSiteFilter(e.target.value)}
              className="px-3 py-1.5 rounded-lg text-sm bg-surface-container-high border border-outline-variant
              text-on-surface focus:outline-none focus:border-primary cursor-pointer"
            >
              <option value="">All sites</option>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>

            <button
              onClick={refreshS3}
              title="Refresh S3 file list"
              className="p-1.5 rounded-lg text-on-surface-variant border border-outline-variant
              hover:bg-surface-container-high transition-colors"
            >
              <svg
                className="w-4 h-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={2}
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                />
              </svg>
            </button>
          </div>
        </div>
      </div>

      {/* ── 30-day compliance headline ── */}
      <div className="flex items-center justify-between gap-6 p-6 rounded-2xl border border-outline-variant bg-surface-container">
        <div className="flex items-baseline gap-2 shrink-0">
          <span className="font-mono text-3xl font-bold text-on-surface">
            {stats.compliance}%
          </span>
          <span className="text-xl text-on-surface-variant">compliant</span>
        </div>
        <div className="min-w-0">
          <p className="text-sm text-on-surface">
            {stats.alerts30} alert{stats.alerts30 !== 1 ? "s" : ""} ·{" "}
            {stats.cas30} corrective action{stats.cas30 !== 1 ? "s" : ""} across{" "}
            {stats.activeDays} active day{stats.activeDays !== 1 ? "s" : ""}
          </p>
          <p className="text-xs text-on-surface-variant mt-0.5">Last 30 days</p>
        </div>
        {stats.trend && (
          <span
            className={clsx(
              "shrink-0 px-3 py-1 rounded-full text-xs font-semibold",
              stats.trend.up
                ? "bg-primary/10 text-primary"
                : "bg-error-container text-error",
            )}
          >
            {stats.trend.label}
          </span>
        )}
      </div>

      {/* ── Two-column body ── */}
      <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-6 items-start">
        {/* Column A: export CTA + day list */}
        <section className="flex flex-col gap-3">
          {/* Sticky export banner */}
          <div className="sticky top-0 z-10 -mx-1 px-1 pt-1 pb-3 bg-surface">
            <div className="flex items-center justify-between gap-4 p-4 rounded-xl border border-outline-variant bg-surface-container-high">
              <div>
                <p className="text-sm font-semibold text-on-surface">
                  Today's HACCP Report
                </p>
                <p className="text-xs text-on-surface-variant mt-0.5">
                  {todayEvents.length} event
                  {todayEvents.length !== 1 ? "s" : ""} ·{" "}
                  {format(new Date(), "EEE, MMM d yyyy")}
                </p>
              </div>
              <ExportButton
                events={todayEvents}
                siteId={exportSiteId}
                date={today}
                addToast={addToast}
                size="md"
                onUploaded={refreshS3}
              />
            </div>
          </div>

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
            <div className="rounded-2xl border border-outline-variant bg-surface-container divide-y divide-outline-variant overflow-hidden">
              {byDate.map(([date, dayEvts]) => (
                <DayCard
                  key={date}
                  date={date}
                  dayEvents={dayEvts}
                  siteId={siteFilter || "all"}
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
              <svg
                className="w-8 h-8 mx-auto text-on-surface-variant opacity-40 mb-3"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={1.5}
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"
                />
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
                <div
                  key={file.key}
                  className="flex items-start gap-3 px-4 py-3 hover:bg-surface-container-high transition-colors"
                >
                  <svg
                    className="w-4 h-4 mt-0.5 shrink-0 text-on-surface-variant"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth={1.5}
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                    />
                  </svg>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-on-surface truncate">
                      {file.date || file.key.split("/").pop()}
                      {file.site_id && (
                        <span className="ml-2 text-xs text-on-surface-variant font-normal">
                          {file.site_id}
                        </span>
                      )}
                    </p>
                    <p className="text-xs text-on-surface-variant mt-0.5">
                      {formatBytes(file.size)} · uploaded{" "}
                      {formatDistanceToNow(parseISO(file.last_modified), {
                        addSuffix: true,
                      })}
                    </p>
                  </div>
                  <button
                    className="shrink-0 px-2.5 py-1 rounded-lg text-xs font-semibold bg-primary-container text-on-primary hover:bg-primary/80 transition-colors"
                    onClick={() =>
                      presignPdf(file.key).then((url) =>
                        window.open(url, "_blank"),
                      )
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
  );
}
