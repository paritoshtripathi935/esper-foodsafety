import { useMemo, useState } from "react";
import { Navigate, useParams } from "react-router-dom";
import Breadcrumb from "../components/common/Breadcrumb";
import { format, formatDistanceToNow, parseISO, subHours } from "date-fns";
import clsx from "clsx";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ReferenceDot,
} from "recharts";
import { useRegistry } from "../hooks/useRegistry";
import { useRealtimeEvents } from "../hooks/useRealtimeEvents";
import AlertDetail from "../components/alerts/AlertDetail";
import type { FoodSafetyEvent } from "../types";

// ─── zone badge ───────────────────────────────────────────────────────────────

const ZONE_LABEL = {
  cold: "❄ Cold",
  hot: "🔥 Hot",
  frozen: "🧊 Frozen",
  prep: "🥬 Prep",
} as const;
const ZONE_PILL = {
  cold: "bg-primary/10 text-primary",
  hot: "bg-error-container/60 text-error",
  frozen: "bg-primary/20 text-primary",
  prep: "bg-tertiary/20 text-tertiary",
} as const;

// ─── event-log helpers ────────────────────────────────────────────────────────

const TYPE_LABEL: Record<string, string> = {
  temp: "Temp",
  alert: "Alert",
  timer: "Timer",
  corrective_action: "Corrective action",
};
const TYPE_COLOR: Record<string, string> = {
  temp: "text-on-surface-variant",
  alert: "text-error font-semibold",
  timer: "text-primary",
  corrective_action: "text-primary font-semibold",
};

function eventDetail(e: FoodSafetyEvent): string {
  const p = e.payload as Record<string, unknown>;
  switch (e.type) {
    case "temp":
      return `${Number(e.value).toFixed(1)}°F`;
    case "alert":
      return `${Number(e.value).toFixed(1)}°F (limit ${p.threshold}°F)`;
    case "timer": {
      const ev = String(p.event ?? "");
      return ev === "start"
        ? `${p.batch_label ?? ""} started — ${p.hold_minutes ?? "?"} min hold`
        : `${p.batch_label ?? ""} ${ev}`;
    }
    case "corrective_action":
      return String(p.action_taken ?? "logged");
    default:
      return "";
  }
}

// ─── chart tooltip ────────────────────────────────────────────────────────────

function ChartTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: Array<{ payload: { ts: number; value: number } }>;
}) {
  if (!active || !payload?.length) return null;
  const { ts, value } = payload[0].payload;
  return (
    <div className="px-2.5 py-1.5 rounded-lg bg-surface-container border border-outline-variant text-xs shadow-lg">
      <span className="font-mono text-on-surface-variant">
        {format(new Date(ts), "HH:mm")}
      </span>
      <span className="mx-1.5 text-outline">·</span>
      <span className="font-mono font-semibold text-on-surface">
        {value.toFixed(1)}°F
      </span>
    </div>
  );
}

// ─── main page ────────────────────────────────────────────────────────────────

export default function StationDetail() {
  const { siteId, station: stationSlug } = useParams<{
    siteId: string;
    station: string;
  }>();
  const { sites, stations: stationRegistry } = useRegistry();
  const { events } = useRealtimeEvents();
  const [selectedAlert, setSelectedAlert] = useState<FoodSafetyEvent | null>(
    null,
  );

  const site = sites.find((s) => s.id === siteId);
  const stationRecord = stationRegistry.find(
    (s) => s.site_id === siteId && s.slug === stationSlug,
  );

  // Fallback display name when registry hasn't loaded yet
  const stationName =
    stationRecord?.name ??
    (stationSlug
      ? stationSlug.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase())
      : "");

  // All events for this site+station
  const stationEvents = useMemo(
    () =>
      events.filter((e) => e.site_id === siteId && e.station === stationName),
    [events, siteId, stationName],
  );

  // Last-24h temp data points for the chart — recomputed on every render so the window slides with realtime updates
  const now = Date.now();
  const cutoff = subHours(new Date(), 24).getTime();

  const chartData = useMemo(
    () =>
      stationEvents
        .filter((e) => e.type === "temp" && new Date(e.ts).getTime() >= cutoff)
        .sort((a, b) => new Date(a.ts).getTime() - new Date(b.ts).getTime())
        .map((e) => {
          const value = Number(e.value);
          return {
            ts: new Date(e.ts).getTime(),
            value,
            alertValue:
              stationRecord?.max_temp_f != null && value > stationRecord.max_temp_f
                ? value
                : null,
          };
        }),
    [stationEvents, cutoff, stationRecord],
  );

  // Annotation events in same 24h window
  const alertDots = useMemo(
    () =>
      stationEvents.filter(
        (e) => e.type === "alert" && new Date(e.ts).getTime() >= cutoff,
      ),
    [stationEvents, cutoff],
  );
  const caDots = useMemo(
    () =>
      stationEvents.filter(
        (e) =>
          e.type === "corrective_action" && new Date(e.ts).getTime() >= cutoff,
      ),
    [stationEvents, cutoff],
  );

// Y-axis domain: pad ±5° around data range, also include thresholds
  const yDomain = useMemo((): [number | string, number | string] => {
    const values = chartData.map((d) => d.value);
    if (stationRecord?.max_temp_f != null)
      values.push(stationRecord.max_temp_f);
    if (stationRecord?.min_temp_f != null)
      values.push(stationRecord.min_temp_f);
    if (values.length === 0) return ["auto", "auto"];
    const lo = Math.min(...values);
    const hi = Math.max(...values);
    return [Math.floor(lo - 5), Math.ceil(hi + 5)];
  }, [chartData, stationRecord]);

  // Current temp
  const currentTemp = useMemo(() => {
    const latest = [...stationEvents]
      .filter((e) => e.type === "temp")
      .sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime())[0];
    return latest ? Number(latest.value) : null;
  }, [stationEvents]);

  // Last 20 events for the log, newest first
  const recentEvents = useMemo(
    () =>
      [...stationEvents]
        .sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime())
        .slice(0, 20),
    [stationEvents],
  );

  // X-axis: tick every 2 hours across the full 24h window
  const xTicks = useMemo(() => {
    const ticks: number[] = [];
    const cursor = new Date(cutoff);
    cursor.setMinutes(0, 0, 0);
    cursor.setSeconds(0, 0);
    cursor.setHours(cursor.getHours() + 1); // first full hour after window start
    while (cursor.getTime() <= now) {
      ticks.push(cursor.getTime());
      cursor.setHours(cursor.getHours() + 2);
    }
    return ticks;
  }, [cutoff, now]);

  // Navigate away if registry loaded and site/station not found
  if (sites.length > 0 && !site) return <Navigate to="/sites" replace />;
  if (stationRegistry.length > 0 && !stationRecord && stationSlug) {
    // Station may only be in events, not the registry — don't hard-redirect
  }

  console.log(recentEvents);

  return (
    <div className="flex flex-col gap-6 p-6 max-w-[1400px]">
      {/* ── Header ── */}
      <div className="sticky top-0 z-10 bg-surface-container/95 backdrop-blur border-b border-outline-variant -mx-6 px-6 py-4">
        <div className="flex flex-col gap-2">
          <Breadcrumb
            trail={[
              { label: "Sites", to: "/sites" },
              { label: site?.name ?? "Site", to: `/sites/${siteId}` },
              { label: stationName },
            ]}
          />
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-2xl font-bold text-on-surface">
              {stationName}
            </h1>

            {/* Zone badge */}
            {stationRecord && (
              <span
                className={clsx(
                  "px-2.5 py-0.5 rounded-full text-xs font-semibold",
                  ZONE_PILL[stationRecord.zone],
                )}
              >
                {ZONE_LABEL[stationRecord.zone]}
              </span>
            )}

            {/* Threshold chips */}
            {stationRecord?.max_temp_f != null && (
              <span
                className="flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs
                bg-error-container/40 text-error border border-error/20"
              >
                Max {stationRecord.max_temp_f}°F
              </span>
            )}
            {stationRecord?.min_temp_f != null && (
              <span
                className="flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs
                bg-tertiary/10 text-tertiary border border-tertiary/20"
              >
                Min {stationRecord.min_temp_f}°F
              </span>
            )}

            {/* Current temp */}
            {currentTemp !== null && (
              <span className="ml-auto font-mono text-3xl font-bold text-on-surface tabular-nums">
                {currentTemp.toFixed(1)}
                <span className="text-xl text-on-surface-variant">°F</span>
              </span>
            )}
          </div>
        </div>
      </div>

      {/* ── Chart card ── */}
      <div className="rounded-2xl border border-outline-variant bg-surface-container p-5 flex flex-col gap-4">
        <h2 className="text-sm font-semibold text-on-surface">Last 24 hours</h2>

        {chartData.length === 0 ? (
          <div className="h-[320px] flex items-center justify-center text-sm text-on-surface-variant">
            No temperature data in the last 24 hours.
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={320}>
            <LineChart
              data={chartData}
              margin={{ top: 8, right: 16, bottom: 0, left: 0 }}
            >
              <CartesianGrid
                strokeDasharray="3 3"
                stroke="rgba(128,128,128,0.15)"
              />

              <XAxis
                dataKey="ts"
                scale="time"
                type="number"
                domain={[cutoff, now]}
                ticks={xTicks}
                tickFormatter={(v) => format(new Date(v), "HH:mm")}
                tick={{
                  fontSize: 11,
                  fill: "var(--color-on-surface-variant, #888)",
                }}
                tickLine={false}
                axisLine={false}
              />

              <YAxis
                domain={yDomain}
                tickFormatter={(v) => `${v}°`}
                tick={{
                  fontSize: 11,
                  fill: "var(--color-on-surface-variant, #888)",
                }}
                tickLine={false}
                axisLine={false}
                width={36}
              />

              <Tooltip content={<ChartTooltip />} />

              {/* Threshold reference lines */}
              {stationRecord?.max_temp_f != null && (
                <ReferenceLine
                  y={stationRecord.max_temp_f}
                  stroke="var(--color-error, #ef4444)"
                  strokeOpacity={0.6}
                  strokeDasharray="5 3"
                  label={{
                    value: `max ${stationRecord.max_temp_f}°F`,
                    position: "insideTopRight",
                    fontSize: 10,
                    fill: "var(--color-error, #ef4444)",
                  }}
                />
              )}
              {stationRecord?.min_temp_f != null && (
                <ReferenceLine
                  y={stationRecord.min_temp_f}
                  stroke="var(--color-tertiary, #b45309)"
                  strokeOpacity={0.6}
                  strokeDasharray="5 3"
                  label={{
                    value: `min ${stationRecord.min_temp_f}°F`,
                    position: "insideBottomRight",
                    fontSize: 10,
                    fill: "var(--color-tertiary, #b45309)",
                  }}
                />
              )}

              {/* Temp line — full trace */}
              <Line
                type="monotone"
                dataKey="value"
                stroke="var(--color-primary, #6366f1)"
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4, strokeWidth: 0 }}
              />

              {/* Above-threshold overlay — red segment + explicit dots at each violating reading */}
              {stationRecord?.max_temp_f != null && (
                <Line
                  type="monotone"
                  dataKey="alertValue"
                  stroke="var(--color-error, #ef4444)"
                  strokeWidth={2.5}
                  connectNulls={false}
                  isAnimationActive={false}
                  dot={(props: { cx: number; cy: number; value: number | null; index: number }) => {
                    if (props.value == null) return <g key={props.index} />;
                    return (
                      <circle
                        key={props.index}
                        cx={props.cx}
                        cy={props.cy}
                        r={4}
                        fill="var(--color-error, #ef4444)"
                        stroke="var(--color-surface-container, #1e1e2e)"
                        strokeWidth={1.5}
                      />
                    );
                  }}
                  activeDot={false}
                />
              )}

              {/* Alert dots */}
              {alertDots.map((e) => {
                const ts = new Date(e.ts).getTime();
                const nearest = chartData.reduce(
                  (best, d) =>
                    Math.abs(new Date(d.ts).getTime() - ts) <
                    Math.abs(new Date(best.ts).getTime() - ts)
                      ? d
                      : best,
                  chartData[0],
                );
                return (
                  <ReferenceDot
                    key={e.id}
                    x={new Date(nearest.ts).getTime()}
                    y={nearest.value}
                    r={6}
                    fill="var(--color-error, #ef4444)"
                    stroke="var(--color-surface-container, #1e1e2e)"
                    strokeWidth={2}
                  />
                );
              })}

              {/* Corrective-action dots */}
              {caDots.map((e) => {
                const ts = new Date(e.ts).getTime();
                const nearest = chartData.reduce(
                  (best, d) =>
                    Math.abs(new Date(d.ts).getTime() - ts) <
                    Math.abs(new Date(best.ts).getTime() - ts)
                      ? d
                      : best,
                  chartData[0],
                );
                return (
                  <ReferenceDot
                    key={e.id}
                    x={new Date(nearest.ts).getTime()}
                    y={nearest.value}
                    r={6}
                    fill="var(--color-primary, #6366f1)"
                    stroke="var(--color-surface-container, #1e1e2e)"
                    strokeWidth={2}
                  />
                );
              })}
            </LineChart>
          </ResponsiveContainer>
        )}

        {/* Legend */}
        <div className="flex items-center gap-4 text-xs text-on-surface-variant">
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-0.5 bg-primary rounded" />
            Temperature
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-0.5 bg-error rounded" />
            Above threshold
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-primary" />
            Corrective action
          </span>
        </div>
      </div>

      {/* ── Recent events ── */}
      <div className="rounded-2xl border border-outline-variant bg-surface-container overflow-hidden">
        <div className="px-5 py-3 border-b border-outline-variant">
          <h2 className="text-sm font-semibold text-on-surface">
            Recent events
            <span className="ml-2 text-on-surface-variant font-normal">
              (last {recentEvents.length})
            </span>
          </h2>
        </div>

        {recentEvents.length === 0 ? (
          <p className="px-5 py-4 text-sm text-on-surface-variant">
            No events yet for this station.
          </p>
        ) : (
          <div className="divide-y divide-outline-variant/50">
            {recentEvents.map((e) => {
              const isAlert = e.type === "alert";
              const Row = isAlert ? "button" : "div";
              return (
                <Row
                  key={e.id}
                  {...(isAlert ? { onClick: () => setSelectedAlert(e) } : {})}
                  className={clsx(
                    "w-full text-left flex items-baseline gap-3 px-5 py-2.5 text-xs",
                    isAlert &&
                      "hover:bg-error-container/10 cursor-pointer transition-colors",
                  )}
                >
                  <span className="shrink-0 font-mono text-on-surface-variant w-11">
                    {format(parseISO(e.ts), "HH:mm")}
                  </span>
                  <span className={clsx("shrink-0 w-28", TYPE_COLOR[e.type])}>
                    {TYPE_LABEL[e.type] ?? e.type}
                  </span>
                  <span className="text-on-surface-variant truncate">
                    {eventDetail(e)}
                  </span>
                  {isAlert && (
                    <span className="ml-auto shrink-0 text-on-surface-variant opacity-60">
                      <svg
                        className="w-3 h-3 inline"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        strokeWidth={2}
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M9 5l7 7-7 7"
                        />
                      </svg>
                    </span>
                  )}
                </Row>
              );
            })}
          </div>
        )}
      </div>

      {/* Alert detail modal */}
      {selectedAlert && (
        <AlertDetail
          alert={selectedAlert}
          allEvents={events}
          onClose={() => setSelectedAlert(null)}
        />
      )}
    </div>
  );
}
