export type { Site, Device, Station } from '../hooks/useRegistry'

export type EventType = 'temp' | 'alert' | 'timer' | 'corrective_action'

export interface FoodSafetyEvent {
  id: string
  device_id: string
  site_id: string
  station: string
  probe_id: string | null
  type: EventType
  value: number
  ts: string
  payload: Record<string, unknown>
}

export interface StationState {
  station: string
  name: string
  site_id: string
  latestTemp: number | null
  openTimer: {
    batch_label: string
    hold_minutes: number
    started_at: string
  } | null
  activeAlert: FoodSafetyEvent | null
}

export type AlertSeverity = 'low' | 'medium' | 'high' | 'critical'

export interface CorrectiveAction {
  action_taken?: string
  root_cause?: string
  disposition?: string
  severity?: AlertSeverity
  alert_event_id?: string
  narrative?: string
}
