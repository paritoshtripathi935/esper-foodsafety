import { useMemo } from "react";
import { useOutletContext } from "react-router-dom";
import { formatDistanceToNow, parseISO, isToday } from "date-fns";
import clsx from "clsx";
import { useDerivedStationState } from "../hooks/useDerivedStationState";
import MultiSiteRollup from "../components/common/MultiSiteRollup";
import StationBoard from "../components/dashboard/StationBoard";
import type { FoodSafetyEvent, Site } from "../types";
import type { Station } from "../hooks/useRegistry";

interface ShellContext {
  events: FoodSafetyEvent[];
  sites: Site[];
  stationRegistry: Station[];
  selectedSite: string | null;
  setSelectedSite: (site: string | null) => void;
  addToast: (msg: string) => void;
  handleSeedDemo: () => Promise<void>;
}

interface KpiTileProps {
  label: string;
  value: string;
  subtitle?: string;
  alert?: boolean;
  error?: boolean;
}

function KpiTile({ label, value, subtitle, alert, error }: KpiTileProps) {
  return (
    <div
      className={clsx(
        "rounded-xl p-4 flex flex-col gap-1",
        "bg-surface-container",
        alert && "animate-pulse ring-1 ring-error/40",
      )}
    >
      <span className="text-[10px] font-semibold uppercase tracking-widest text-on-surface-variant">
        {label}
      </span>
      <span
        className={clsx(
          "font-mono text-2xl font-bold leading-tight",
          alert || error ? "text-error" : "text-on-surface",
        )}
      >
        {value}
      </span>
      {subtitle && (
        <span className="text-xs text-on-surface-variant">{subtitle}</span>
      )}
    </div>
  );
}

export default function Overview() {
  const {
    events,
    sites,
    stationRegistry,
    selectedSite,
    setSelectedSite,
    handleSeedDemo,
  } = useOutletContext<ShellContext>();

  const stations = useDerivedStationState(
    events,
    selectedSite,
    stationRegistry,
  );

  const kpi = useMemo(() => {
    const now = Date.now();
    const tenMin = 10 * 60 * 1000;

    // 1. Stations online — registry stations with latestTemp not null and last event < 10 min
    const totalRegistryStations = stationRegistry.length;
    const onlineCount = stations.filter((s) => {
      if (s.latestTemp === null) return false;
      // find most recent event for this station
      const stationEvents = events.filter(
        (e) => e.site_id === s.site_id && e.station === s.station,
      );
      if (stationEvents.length === 0) return false;
      const latest = Math.max(
        ...stationEvents.map((e) => new Date(e.ts).getTime()),
      );
      return now - latest < tenMin;
    });
    const totalY =
      totalRegistryStations > 0 ? totalRegistryStations : stations.length;

    // 2. Active alerts
    const activeAlerts = stations.filter((s) => s.activeAlert != null).length;

    // 3. Corrective actions today
    const correctiveActionsToday = events.filter(
      (e) => e.type === "corrective_action" && isToday(parseISO(e.ts)),
    ).length;

    // 4. Devices offline — stations with no event in last 10 min OR no events at all
    const offlineCount = stations.filter((s) => {
      const stationEvents = events.filter(
        (e) => e.site_id === s.site_id && e.station === s.station,
      );
      if (stationEvents.length === 0) return true;
      const latest = Math.max(
        ...stationEvents.map((e) => new Date(e.ts).getTime()),
      );
      return now - latest >= tenMin;
    }).length;

    // 5. Compliance % — vibe number based on alerts today vs temp readings today
    const alertsToday = events.filter(
      (e) => e.type === "alert" && isToday(parseISO(e.ts)),
    ).length;
    const tempsToday = events.filter(
      (e) => e.type === "temp" && isToday(parseISO(e.ts)),
    ).length;
    const denom = alertsToday + Math.max(tempsToday, 1);
    const compliance = Math.round(100 - (alertsToday / denom) * 100);

    // Last update timestamp
    const allTs = events.map((e) => new Date(e.ts).getTime()).filter(Boolean);
    const lastTs = allTs.length > 0 ? new Date(Math.max(...allTs)) : null;

    return {
      onlineCount: onlineCount.length,
      totalY,
      activeAlerts,
      correctiveActionsToday,
      offlineCount,
      compliance,
      lastTs,
    };
  }, [events, stations, stationRegistry]);

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {sites.length > 1 && (
        <div className="px-5 pt-4 shrink-0">
          <MultiSiteRollup
            sites={sites}
            stations={stations}
            alerts={[]}
            selectedSiteId={selectedSite}
            onSelectSite={setSelectedSite}
          />
        </div>
      )}

      {/* KPI strip */}
      <div className="px-5 pt-8 shrink-0">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-sm font-medium text-on-surface-variant uppercase tracking-wide">
            Snapshot
          </h2>
          {kpi.lastTs && (
            <span className="text-xs text-on-surface-variant">
              Last update {formatDistanceToNow(kpi.lastTs, { addSuffix: true })}
            </span>
          )}
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-3">
          <KpiTile
            label="Stations Online"
            value={`${kpi.onlineCount} of ${kpi.totalY}`}
          />
          <KpiTile
            label="Active Alerts"
            value={String(kpi.activeAlerts)}
            alert={kpi.activeAlerts > 0}
          />
          <KpiTile
            label="Corrective Actions"
            value={String(kpi.correctiveActionsToday)}
            subtitle="logged today"
          />
          <KpiTile
            label="Devices Offline"
            value={String(kpi.offlineCount)}
            error={kpi.offlineCount > 0}
          />
          <KpiTile
            label="Compliance %"
            value={`${kpi.compliance}%`}
            subtitle="today"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-5">
        <h2 className="text-sm font-medium text-on-surface-variant uppercase tracking-wide mb-5">
          Devices
        </h2>
        <StationBoard stations={stations} onSeedDemo={handleSeedDemo} />
      </div>
    </div>
  );
}
