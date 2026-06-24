import { useMemo, useState, useEffect } from "react";
import { NavLink, useLocation } from "react-router-dom";
import clsx from "clsx";
import { formatDistanceToNow, parseISO } from "date-fns";
import type { Site, Device, Station } from "../hooks/useRegistry";
import type { FoodSafetyEvent } from "../types";

interface Props {
  sites: Site[];
  devices: Device[];
  stations: Station[];
  events: FoodSafetyEvent[];
}

type DeviceStatus = "online" | "stale" | "offline";
type SiteStatus = "ok" | "warn" | "alert";

const ZONE_ICON: Record<Station["zone"], string> = {
  cold: "❄",
  hot: "🔥",
  frozen: "🧊",
  prep: "🥬",
};
const KIND_ICON: Record<Device["kind"], string> = {
  kiosk: "📱",
  "probe-sim": "🔌",
};
const STATUS_DOT: Record<SiteStatus, string> = {
  ok: "bg-primary",
  warn: "bg-tertiary",
  alert: "bg-error",
};
const DEVICE_PILL: Record<DeviceStatus, string> = {
  online: "bg-primary/20 text-primary",
  stale: "bg-tertiary/20 text-tertiary",
  offline: "bg-error-container text-error",
};

function deviceStatus(lastSeen: string | null): DeviceStatus {
  if (!lastSeen) return "offline";
  const ageMs = Date.now() - new Date(lastSeen).getTime();
  if (ageMs < 2 * 60_000) return "online";
  if (ageMs < 10 * 60_000) return "stale";
  return "offline";
}

function useTreeData(events: FoodSafetyEvent[]) {
  return useMemo(() => {
    // Latest temp per site_id::slug
    const latestTempMap = new Map<string, number>();
    // Active alert count per site_id
    const alertCountMap = new Map<string, number>();

    // Build latest temp: scan all temp events, keep newest per station key
    const tempEvents = events
      .filter((e) => e.type === "temp")
      .sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime());
    for (const e of tempEvents) {
      const key = `${e.site_id}::${e.station}`;
      if (!latestTempMap.has(key)) latestTempMap.set(key, Number(e.value));
    }

    // Active alerts: alert events with no later corrective_action for same station
    const alertEvents = events.filter((e) => e.type === "alert");
    for (const alert of alertEvents) {
      const tAlert = new Date(alert.ts).getTime();
      const isActive = !events.some(
        (e) =>
          e.type === "corrective_action" &&
          e.site_id === alert.site_id &&
          e.station === alert.station &&
          new Date(e.ts).getTime() > tAlert,
      );
      if (isActive) {
        alertCountMap.set(
          alert.site_id,
          (alertCountMap.get(alert.site_id) ?? 0) + 1,
        );
      }
    }

    return { latestTempMap, alertCountMap };
  }, [events]);
}

function siteStatus(
  siteId: string,
  stations: Station[],
  latestTempMap: Map<string, number>,
  alertCountMap: Map<string, number>,
): SiteStatus {
  if ((alertCountMap.get(siteId) ?? 0) > 0) return "alert";
  for (const s of stations) {
    if (s.site_id !== siteId) continue;
    const temp = latestTempMap.get(`${siteId}::${s.slug}`);
    if (temp !== undefined && temp > 40) return "warn";
  }
  return "ok";
}

// Shared active-row style for device + station NavLinks
function rowClass({ isActive }: { isActive: boolean }) {
  return clsx(
    "flex items-center gap-1.5 w-full px-2 py-1 text-xs rounded-sm transition-colors",
    "hover:bg-surface-container-high",
    isActive
      ? "border-l-2 border-primary bg-primary/10 text-primary pl-[6px]"
      : "border-l-2 border-transparent text-on-surface-variant",
  );
}

export default function SitesTree({ sites, devices, stations, events }: Props) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const { latestTempMap, alertCountMap } = useTreeData(events);
  const { pathname } = useLocation();

  // Auto-expand the active site when navigating to /sites/:siteId/...
  useEffect(() => {
    const match = pathname.match(/^\/sites\/([^/]+)/);
    if (match) {
      setExpanded((prev) => {
        if (prev.has(match[1])) return prev;
        const next = new Set(prev);
        next.add(match[1]);
        return next;
      });
    }
  }, [pathname]);

  function toggle(siteId: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      next.has(siteId) ? next.delete(siteId) : next.add(siteId);
      return next;
    });
  }

  return (
    <div>
      {/* "Sites" header — navigates to /sites index */}
      <NavLink
        to="/sites"
        className={({ isActive }) =>
          clsx(
            "flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors",
            isActive
              ? "bg-primary/10 text-primary"
              : "text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface",
          )
        }
      >
        <svg
          className="w-5 h-5 shrink-0"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.75}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-2 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"
          />
        </svg>
        Sites
      </NavLink>
    </div>
  );
}
