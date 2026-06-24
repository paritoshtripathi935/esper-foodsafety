import { useMemo, useState } from 'react'
import { NavLink } from 'react-router-dom'
import clsx from 'clsx'
import { formatDistanceToNow, parseISO } from 'date-fns'
import type { Site, Device, Station } from '../hooks/useRegistry'
import type { FoodSafetyEvent } from '../types'

interface Props {
  sites: Site[]
  devices: Device[]
  stations: Station[]
  events: FoodSafetyEvent[]
}

type DeviceStatus = 'online' | 'stale' | 'offline'
type SiteStatus = 'ok' | 'warn' | 'alert'

const ZONE_ICON: Record<Station['zone'], string> = {
  cold: '❄',
  hot: '🔥',
  frozen: '🧊',
  prep: '🥬',
}
const KIND_ICON: Record<Device['kind'], string> = {
  kiosk: '📱',
  'probe-sim': '🔌',
}
const STATUS_DOT: Record<SiteStatus, string> = {
  ok: 'bg-primary',
  warn: 'bg-tertiary',
  alert: 'bg-error',
}
const DEVICE_PILL: Record<DeviceStatus, string> = {
  online: 'bg-primary/20 text-primary',
  stale: 'bg-tertiary/20 text-tertiary',
  offline: 'bg-error-container text-error',
}

function deviceStatus(lastSeen: string | null): DeviceStatus {
  if (!lastSeen) return 'offline'
  const ageMs = Date.now() - new Date(lastSeen).getTime()
  if (ageMs < 2 * 60_000) return 'online'
  if (ageMs < 10 * 60_000) return 'stale'
  return 'offline'
}

function useTreeData(events: FoodSafetyEvent[]) {
  return useMemo(() => {
    // Latest temp per site_id::slug
    const latestTempMap = new Map<string, number>()
    // Active alert count per site_id
    const alertCountMap = new Map<string, number>()

    // Build latest temp: scan all temp events, keep newest per station key
    const tempEvents = events
      .filter((e) => e.type === 'temp')
      .sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime())
    for (const e of tempEvents) {
      const key = `${e.site_id}::${e.station}`
      if (!latestTempMap.has(key)) latestTempMap.set(key, Number(e.value))
    }

    // Active alerts: alert events with no later corrective_action for same station
    const alertEvents = events.filter((e) => e.type === 'alert')
    for (const alert of alertEvents) {
      const tAlert = new Date(alert.ts).getTime()
      const isActive = !events.some(
        (e) =>
          e.type === 'corrective_action' &&
          e.site_id === alert.site_id &&
          e.station === alert.station &&
          new Date(e.ts).getTime() > tAlert
      )
      if (isActive) {
        alertCountMap.set(alert.site_id, (alertCountMap.get(alert.site_id) ?? 0) + 1)
      }
    }

    return { latestTempMap, alertCountMap }
  }, [events])
}

function siteStatus(
  siteId: string,
  stations: Station[],
  latestTempMap: Map<string, number>,
  alertCountMap: Map<string, number>
): SiteStatus {
  if ((alertCountMap.get(siteId) ?? 0) > 0) return 'alert'
  for (const s of stations) {
    if (s.site_id !== siteId) continue
    const temp = latestTempMap.get(`${siteId}::${s.slug}`)
    if (temp !== undefined && temp > 40) return 'warn'
  }
  return 'ok'
}

// Shared active-row style for device + station NavLinks
function rowClass({ isActive }: { isActive: boolean }) {
  return clsx(
    'flex items-center gap-1.5 w-full px-2 py-1 text-xs rounded-sm transition-colors',
    'hover:bg-surface-container-high',
    isActive
      ? 'border-l-2 border-primary bg-primary/10 text-primary pl-[6px]'
      : 'border-l-2 border-transparent text-on-surface-variant'
  )
}

export default function SitesTree({ sites, devices, stations, events }: Props) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const { latestTempMap, alertCountMap } = useTreeData(events)

  function toggle(siteId: string) {
    setExpanded((prev) => {
      const next = new Set(prev)
      next.has(siteId) ? next.delete(siteId) : next.add(siteId)
      return next
    })
  }

  return (
    <div>
      {/* "Sites" header — navigates to /sites index */}
      <NavLink
        to="/sites"
        end
        className={({ isActive }) =>
          clsx(
            'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
            isActive
              ? 'bg-primary/10 text-primary'
              : 'text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface'
          )
        }
      >
        <svg className="w-5 h-5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-2 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
        </svg>
        Sites
      </NavLink>

      {/* Per-site expandable rows */}
      {sites.map((site) => {
        const isOpen = expanded.has(site.id)
        const status = siteStatus(site.id, stations, latestTempMap, alertCountMap)
        const alertCount = alertCountMap.get(site.id) ?? 0
        const siteDevices = devices.filter((d) => d.site_id === site.id)
        const siteStations = stations.filter((s) => s.site_id === site.id)

        return (
          <div key={site.id}>
            {/* Site row — toggle only, does not navigate */}
            <button
              onClick={() => toggle(site.id)}
              className="flex items-center gap-2 w-full px-3 py-1.5 text-xs font-medium text-on-surface-variant
                hover:bg-surface-container-high hover:text-on-surface transition-colors rounded-lg"
            >
              {/* Chevron */}
              <svg
                className={clsx('w-3 h-3 shrink-0 transition-transform', isOpen && 'rotate-90')}
                fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
              </svg>

              {/* Status dot */}
              <span className={clsx('w-1.5 h-1.5 rounded-full shrink-0', STATUS_DOT[status])} />

              {/* Name */}
              <span className="flex-1 text-left truncate">{site.name}</span>

              {/* Alert badge */}
              {alertCount > 0 && (
                <span className="px-1 py-0.5 rounded text-[10px] font-semibold bg-error-container text-error leading-none">
                  {alertCount}
                </span>
              )}
            </button>

            {/* Children */}
            {isOpen && (
              <div className="ml-5 mb-1 flex flex-col gap-px">
                {/* Devices section */}
                {siteDevices.length > 0 && (
                  <>
                    <p className="px-2 pt-1.5 pb-0.5 text-[10px] font-semibold uppercase tracking-wider text-on-surface-variant">
                      Devices
                    </p>
                    {siteDevices.map((device) => {
                      const ds = deviceStatus(device.last_seen)
                      const lastSeenLabel = device.last_seen
                        ? formatDistanceToNow(parseISO(device.last_seen), { addSuffix: true })
                        : 'never'
                      return (
                        <NavLink
                          key={device.id}
                          to={`/sites/${site.id}/devices/${device.id}`}
                          className={rowClass}
                        >
                          <span className="shrink-0">{KIND_ICON[device.kind]}</span>
                          <span className="flex-1 truncate font-mono text-[11px]">{device.name}</span>
                          <span
                            title={`Last seen ${lastSeenLabel}`}
                            className={clsx(
                              'shrink-0 px-1 py-0.5 rounded text-[9px] font-semibold leading-none',
                              DEVICE_PILL[ds]
                            )}
                          >
                            {ds}
                          </span>
                        </NavLink>
                      )
                    })}
                  </>
                )}

                {/* Stations section */}
                {siteStations.length > 0 && (
                  <>
                    <p className="px-2 pt-1.5 pb-0.5 text-[10px] font-semibold uppercase tracking-wider text-on-surface-variant">
                      Stations
                    </p>
                    {siteStations.map((station) => {
                      const temp = latestTempMap.get(`${site.id}::${station.slug}`)
                      return (
                        <NavLink
                          key={station.id}
                          to={`/sites/${site.id}/stations/${station.slug}`}
                          className={rowClass}
                        >
                          <span className="shrink-0">{ZONE_ICON[station.zone]}</span>
                          <span className="flex-1 truncate">{station.name}</span>
                          {temp !== undefined && (
                            <span className="shrink-0 font-mono text-[11px] tabular-nums text-on-surface-variant">
                              {temp.toFixed(1)}°
                            </span>
                          )}
                        </NavLink>
                      )
                    })}
                  </>
                )}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
