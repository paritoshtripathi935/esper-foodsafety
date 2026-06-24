import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { supabase } from "../services/supabase";
import { useRealtimeEvents } from "../hooks/useRealtimeEvents";
import { useAlerts } from "../hooks/useAlerts";
import { useRegistry } from "../hooks/useRegistry";
import SitesTree from "./SitesTree";
import AlertsFeed from "../components/alerts/AlertsFeed";
import AlertDetail from "../components/alerts/AlertDetail";
import ErrorToast, { useToasts } from "../components/common/ErrorToast";
import CommandPalette from "../components/common/CommandPalette";
import { seedDemo } from "../utils/seed-demo";
import type { FoodSafetyEvent, Site } from "../types";

type SupabaseStatus = "pending" | "connected" | "error";

const ALERTS_RAIL_ROUTES = ["/", "/sites"];

function showAlertsRail(pathname: string): boolean {
  if (pathname === "/" || pathname === "/sites") return true;
  if (pathname.startsWith("/sites/")) return true;
  return false;
}

const NAV_ITEMS = [
  {
    to: "/",
    end: true,
    label: "Overview",
    icon: (
      <svg
        className="w-5 h-5"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={1.75}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"
        />
        <path strokeLinecap="round" strokeLinejoin="round" d="M9 22V12h6v10" />
      </svg>
    ),
  },
  {
    to: "/audit",
    end: false,
    label: "Audit Log",
    icon: (
      <svg
        className="w-5 h-5"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={1.75}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01"
        />
      </svg>
    ),
  },
  {
    to: "/ask",
    end: false,
    label: "Ask AI",
    icon: (
      <svg
        className="w-5 h-5"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={1.75}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M5 3h14a2 2 0 012 2v8a2 2 0 01-2 2H9l-4 4V5a2 2 0 012-2z"
        />
        <path strokeLinecap="round" strokeLinejoin="round" d="M9 9h6M9 12h4" />
      </svg>
    ),
  },
  {
    to: "/reports",
    end: false,
    label: "Reports",
    icon: (
      <svg
        className="w-5 h-5"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={1.75}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
        />
      </svg>
    ),
  },
];

export default function AppShell() {
  const location = useLocation();
  const { events, error: realtimeError } = useRealtimeEvents();
  const [selectedSite, setSelectedSite] = useState<string | null>(null);
  const [selectedAlert, setSelectedAlert] = useState<FoodSafetyEvent | null>(
    null,
  );
  const [supabaseStatus, setSupabaseStatus] =
    useState<SupabaseStatus>("pending");
  const { toasts, addToast, dismissToast } = useToasts();

  const { sites, devices, stations: stationRegistry } = useRegistry();
  const alerts = useAlerts(events, selectedSite);

  useEffect(() => {
    supabase
      .from("events")
      .select("id")
      .limit(1)
      .then(({ error }) => setSupabaseStatus(error ? "error" : "connected"));
  }, []);

  useEffect(() => {
    if (realtimeError) addToast(realtimeError);
  }, [realtimeError]);

  useEffect(() => {
    if (sites.length === 1 && selectedSite === null) {
      setSelectedSite(sites[0].id);
    }
  }, [sites]);

  async function handleSeedDemo() {
    try {
      addToast("Seeding demo data…");
      await seedDemo();
      addToast("Demo data seeded! Realtime stream will update shortly.");
    } catch (err) {
      addToast(`Seed failed: ${(err as Error).message}`);
    }
  }

  const showRail = showAlertsRail(location.pathname);

  const STATUS_DOT: Record<SupabaseStatus, string> = {
    connected: "bg-primary",
    error: "bg-error animate-pulse",
    pending: "bg-on-surface-variant",
  };

  return (
    <div className="flex h-screen overflow-hidden">
      {/* Left sidebar */}
      <aside className="w-56 shrink-0 bg-surface-container border-r border-outline-variant flex flex-col">
        {/* Brand block */}
        <div className="px-5 pt-5 pb-4 border-b border-outline-variant">
          <div className="flex items-center gap-2">
            <svg
              className="w-5 h-5 text-primary shrink-0"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
              />
            </svg>
            <span className="font-semibold text-on-surface tracking-tight">
              SafeTemp
            </span>
          </div>
          <button
            onClick={() => window.dispatchEvent(new CustomEvent('open-command-palette'))}
            className="mt-3 flex items-center gap-1.5 px-2 py-1 rounded-lg w-full
              bg-surface-container-high border border-outline-variant
              text-xs text-on-surface-variant hover:text-on-surface hover:border-primary/50 transition-colors"
          >
            <svg
              className="w-3 h-3 shrink-0"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z"
              />
            </svg>
            <span className="flex-1 text-left">Search…</span>
            <kbd className="font-mono text-[10px] font-semibold opacity-60">
              ⌘K
            </kbd>
          </button>
        </div>

        {/* Nav items */}
        <nav className="flex-1 overflow-y-auto py-3 px-2 flex flex-col gap-px">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                [
                  "flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors",
                  isActive
                    ? "bg-primary/10 text-primary"
                    : "text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface",
                ].join(" ")
              }
            >
              {item.icon}
              {item.label}
            </NavLink>
          ))}
          <div className="mt-px">
            <SitesTree
              sites={sites}
              devices={devices}
              stations={stationRegistry}
              events={events}
            />
          </div>
        </nav>

        {/* Bottom status block */}
        <div className="px-5 py-3 border-t border-outline-variant">
          <div className="flex items-center justify-between">
            <span className="text-xs text-on-surface-variant">Manager</span>
            <div className="flex items-center gap-1.5">
              <span
                className={`w-1.5 h-1.5 rounded-full ${STATUS_DOT[supabaseStatus]}`}
              />
              <span className="text-xs text-on-surface-variant capitalize">
                {supabaseStatus}
              </span>
            </div>
          </div>
        </div>
      </aside>

      {/* Main area */}
      <div className="flex flex-1 overflow-hidden">
        {/* Page content */}
        <div className="flex-1 overflow-y-auto">
          <Outlet
            context={{
              events,
              sites,
              devices,
              stationRegistry,
              selectedSite,
              setSelectedSite,
              addToast,
              handleSeedDemo,
            }}
          />
        </div>

        {/* Right alerts rail — shown on overview + sites routes */}
        {showRail && (
          <div className="w-80 shrink-0 border-l border-outline-variant overflow-hidden flex flex-col bg-surface-container">
            <AlertsFeed
              alerts={alerts}
              allEvents={events}
              onSelectAlert={setSelectedAlert}
              onSeedDemo={handleSeedDemo}
            />
          </div>
        )}
      </div>

      {selectedAlert && (
        <AlertDetail
          alert={selectedAlert}
          allEvents={events}
          onClose={() => setSelectedAlert(null)}
        />
      )}

      <CommandPalette
        sites={sites}
        devices={devices}
        onSeedDemo={handleSeedDemo}
      />
      <ErrorToast toasts={toasts} onDismiss={dismissToast} />
    </div>
  );
}
