import { format, parseISO, formatDistanceToNow } from 'date-fns'

export function formatTime(ts: string): string {
  try { return format(parseISO(ts), 'HH:mm') } catch { return ts }
}

export function formatDateTime(ts: string): string {
  try { return format(parseISO(ts), 'MMM d, HH:mm') } catch { return ts }
}

export function formatRelative(ts: string): string {
  try { return formatDistanceToNow(parseISO(ts), { addSuffix: true }) } catch { return ts }
}

export function formatTemp(value: number): string {
  return `${value.toFixed(1)}°F`
}

export function todayISO(): string {
  return format(new Date(), 'yyyy-MM-dd')
}
