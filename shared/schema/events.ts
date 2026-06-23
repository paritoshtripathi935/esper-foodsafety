export type EventType = 'temp' | 'timer' | 'corrective_action' | 'alert';
export type Severity = 'low' | 'medium' | 'high' | 'critical';

export interface FoodSafetyEvent {
  id?: string;
  device_id: string;
  site_id: string;
  station: string;
  probe_id: string;
  type: EventType;
  value: number | string;
  ts: string; // ISO 8601
  payload?: {
    action_taken?: string;
    root_cause?: string;
    disposition?: string;
    severity?: Severity;
    narrative?: string;
    threshold?: number;
    rule_id?: string;
    batch_label?: string;
    hold_minutes?: number;
  };
}
