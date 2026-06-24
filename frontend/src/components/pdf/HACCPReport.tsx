import {
  Document,
  Page,
  Text,
  View,
  StyleSheet,
  Font,
} from '@react-pdf/renderer'
import { format, parseISO } from 'date-fns'
import type { FoodSafetyEvent } from '../../types'

const colors = {
  bg: '#0E141B',
  surface: '#1A2027',
  surfaceHigh: '#242A32',
  primary: '#5DDCAA',
  onSurface: '#DDE3ED',
  onSurfaceVariant: '#BCCAC0',
  error: '#FFB4AB',
  tertiary: '#E27069',
  outline: '#3D4A43',
}

const styles = StyleSheet.create({
  page: {
    backgroundColor: colors.bg,
    color: colors.onSurface,
    padding: 40,
    fontSize: 10,
    fontFamily: 'Helvetica',
  },
  header: {
    marginBottom: 24,
    borderBottom: `1pt solid ${colors.outline}`,
    paddingBottom: 12,
  },
  logo: {
    fontSize: 18,
    fontFamily: 'Helvetica-Bold',
    color: colors.primary,
  },
  subtitle: {
    fontSize: 11,
    color: colors.onSurfaceVariant,
    marginTop: 2,
  },
  metaRow: {
    flexDirection: 'row',
    gap: 24,
    marginTop: 8,
  },
  metaItem: {
    color: colors.onSurfaceVariant,
    fontSize: 9,
  },
  metaValue: {
    color: colors.onSurface,
    fontFamily: 'Helvetica-Bold',
  },
  section: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 11,
    fontFamily: 'Helvetica-Bold',
    color: colors.primary,
    marginBottom: 8,
    paddingBottom: 4,
    borderBottom: `0.5pt solid ${colors.outline}`,
  },
  table: {
    width: '100%',
  },
  tableHeader: {
    flexDirection: 'row',
    backgroundColor: colors.surfaceHigh,
    padding: '4 6',
    borderRadius: 2,
    marginBottom: 2,
  },
  tableRow: {
    flexDirection: 'row',
    padding: '3 6',
    borderBottom: `0.5pt solid ${colors.outline}`,
  },
  tableRowAlt: {
    flexDirection: 'row',
    padding: '3 6',
    backgroundColor: colors.surface,
    borderBottom: `0.5pt solid ${colors.outline}`,
  },
  th: {
    fontSize: 8,
    color: colors.onSurfaceVariant,
    fontFamily: 'Helvetica-Bold',
    textTransform: 'uppercase',
  },
  td: {
    fontSize: 9,
    color: colors.onSurface,
  },
  tdError: {
    fontSize: 9,
    color: colors.error,
    fontFamily: 'Helvetica-Bold',
  },
  col1: { flex: 2 },
  col2: { flex: 1.5 },
  col3: { flex: 1 },
  col4: { flex: 1.5 },
  breachRow: {
    padding: 8,
    backgroundColor: '#1a0a0a',
    borderLeft: `3pt solid ${colors.error}`,
    marginBottom: 6,
    borderRadius: 2,
  },
  caRow: {
    padding: 8,
    backgroundColor: colors.surface,
    borderLeft: `3pt solid ${colors.primary}`,
    marginBottom: 6,
    borderRadius: 2,
  },
  label: {
    fontSize: 8,
    color: colors.onSurfaceVariant,
    marginTop: 3,
  },
  value: {
    fontSize: 9,
    color: colors.onSurface,
    marginTop: 1,
  },
  signOff: {
    marginTop: 32,
    paddingTop: 16,
    borderTop: `0.5pt solid ${colors.outline}`,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  signField: {
    flex: 1,
    paddingRight: 20,
  },
  signLine: {
    borderBottom: `0.5pt solid ${colors.onSurfaceVariant}`,
    marginBottom: 4,
    height: 20,
  },
  signLabel: {
    fontSize: 8,
    color: colors.onSurfaceVariant,
  },
  footer: {
    position: 'absolute',
    bottom: 20,
    left: 40,
    right: 40,
    flexDirection: 'row',
    justifyContent: 'space-between',
    color: colors.onSurfaceVariant,
    fontSize: 8,
  },
})

interface Props {
  events: FoodSafetyEvent[]
  siteId: string
  date: string
}

function formatTs(ts: string) {
  try { return format(parseISO(ts), 'HH:mm:ss') } catch { return ts }
}

export default function HACCPReport({ events, siteId, date }: Props) {
  const dayStart = new Date(`${date}T00:00:00Z`)
  const dayEnd = new Date(`${date}T23:59:59Z`)

  const dayEvents = events.filter((e) => {
    const t = new Date(e.ts).getTime()
    return t >= dayStart.getTime() && t <= dayEnd.getTime()
  })

  const tempEvents = dayEvents
    .filter((e) => e.type === 'temp')
    .sort((a, b) => new Date(a.ts).getTime() - new Date(b.ts).getTime())

  const alertEvents = dayEvents
    .filter((e) => e.type === 'alert')
    .sort((a, b) => new Date(a.ts).getTime() - new Date(b.ts).getTime())

  const caEvents = dayEvents
    .filter((e) => e.type === 'corrective_action')

  // Summarize temps by station
  const stationSummary = new Map<string, { min: number; max: number; count: number; last: number }>()
  for (const e of tempEvents) {
    const v = Number(e.value)
    const cur = stationSummary.get(e.station)
    if (!cur) {
      stationSummary.set(e.station, { min: v, max: v, count: 1, last: v })
    } else {
      stationSummary.set(e.station, {
        min: Math.min(cur.min, v),
        max: Math.max(cur.max, v),
        count: cur.count + 1,
        last: v,
      })
    }
  }

  const now = new Date()

  return (
    <Document>
      <Page size="A4" style={styles.page}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.logo}>SafeTemp</Text>
          <Text style={styles.subtitle}>HACCP Daily Temperature Log</Text>
          <View style={styles.metaRow}>
            <Text style={styles.metaItem}>
              Site: <Text style={styles.metaValue}>{siteId.replace(/-/g, ' ').toUpperCase()}</Text>
            </Text>
            <Text style={styles.metaItem}>
              Date: <Text style={styles.metaValue}>{date}</Text>
            </Text>
            <Text style={styles.metaItem}>
              Generated: <Text style={styles.metaValue}>{format(now, 'yyyy-MM-dd HH:mm')}</Text>
            </Text>
          </View>
        </View>

        {/* Temperature Summary */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Temperature Summary by Station</Text>
          <View style={styles.table}>
            <View style={styles.tableHeader}>
              <Text style={[styles.th, styles.col1]}>Station</Text>
              <Text style={[styles.th, styles.col3]}>Readings</Text>
              <Text style={[styles.th, styles.col3]}>Min °F</Text>
              <Text style={[styles.th, styles.col3]}>Max °F</Text>
              <Text style={[styles.th, styles.col3]}>Last °F</Text>
            </View>
            {Array.from(stationSummary.entries()).map(([station, s], i) => (
              <View key={station} style={i % 2 === 0 ? styles.tableRow : styles.tableRowAlt}>
                <Text style={[styles.td, styles.col1]}>{station}</Text>
                <Text style={[styles.td, styles.col3]}>{s.count}</Text>
                <Text style={[styles.td, styles.col3]}>{s.min.toFixed(1)}</Text>
                <Text style={[s.max > 41 ? styles.tdError : styles.td, styles.col3]}>
                  {s.max.toFixed(1)}
                </Text>
                <Text style={[styles.td, styles.col3]}>{s.last.toFixed(1)}</Text>
              </View>
            ))}
          </View>
        </View>

        {/* Breaches */}
        {alertEvents.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Temperature Breaches ({alertEvents.length})</Text>
            {alertEvents.map((alert) => {
              const p = alert.payload as Record<string, unknown>
              return (
                <View key={alert.id} style={styles.breachRow}>
                  <Text style={{ fontSize: 10, color: colors.error, fontFamily: 'Helvetica-Bold' }}>
                    {alert.station} — {Number(alert.value).toFixed(1)}°F at {formatTs(alert.ts)}
                  </Text>
                  {p?.threshold != null && (
                    <Text style={styles.label}>Threshold: {Number(p.threshold).toFixed(0)}°F</Text>
                  )}
                </View>
              )
            })}
          </View>
        )}

        {/* Corrective Actions */}
        {caEvents.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Corrective Actions ({caEvents.length})</Text>
            {caEvents.map((ca) => {
              const p = ca.payload as Record<string, unknown>
              return (
                <View key={ca.id} style={styles.caRow}>
                  <Text style={{ fontSize: 10, fontFamily: 'Helvetica-Bold', color: colors.onSurface }}>
                    {ca.station} · {formatTs(ca.ts)}
                  </Text>
                  {!!p?.action_taken && <Text style={styles.value}>{String(p.action_taken)}</Text>}
                  {!!p?.root_cause && (
                    <>
                      <Text style={styles.label}>Root cause:</Text>
                      <Text style={styles.value}>{String(p.root_cause)}</Text>
                    </>
                  )}
                  {!!p?.disposition && (
                    <>
                      <Text style={styles.label}>Disposition:</Text>
                      <Text style={styles.value}>{String(p.disposition)}</Text>
                    </>
                  )}
                  {!!p?.narrative && (
                    <>
                      <Text style={styles.label}>Narrative:</Text>
                      <Text style={[styles.value, { fontSize: 8, color: colors.onSurfaceVariant }]}>
                        {String(p.narrative)}
                      </Text>
                    </>
                  )}
                </View>
              )
            })}
          </View>
        )}

        {/* Sign-off */}
        <View style={styles.signOff}>
          <View style={styles.signField}>
            <View style={styles.signLine} />
            <Text style={styles.signLabel}>Manager Signature</Text>
          </View>
          <View style={styles.signField}>
            <View style={styles.signLine} />
            <Text style={styles.signLabel}>Date</Text>
          </View>
          <View style={styles.signField}>
            <View style={styles.signLine} />
            <Text style={styles.signLabel}>Print Name</Text>
          </View>
        </View>

        {/* Footer */}
        <View style={styles.footer} fixed>
          <Text>SafeTemp HACCP Report · {siteId}</Text>
          <Text render={({ pageNumber, totalPages }) => `${pageNumber} / ${totalPages}`} />
        </View>
      </Page>
    </Document>
  )
}
