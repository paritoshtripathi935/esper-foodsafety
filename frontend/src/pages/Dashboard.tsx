import { useEffect, useState } from 'react'
import { format } from 'date-fns'
import { supabase } from '../services/supabase'
import { useRealtimeEvents } from '../hooks/useRealtimeEvents'
import { useDerivedStationState } from '../hooks/useDerivedStationState'
import { useAlerts } from '../hooks/useAlerts'
import { useSites } from '../hooks/useSites'
import { seedDemo } from '../utils/seed-demo'
import TopBar from '../components/common/TopBar'
import MultiSiteRollup from '../components/common/MultiSiteRollup'
import StationBoard from '../components/dashboard/StationBoard'
import AlertsFeed from '../components/alerts/AlertsFeed'
import AlertDetail from '../components/alerts/AlertDetail'
import ErrorToast, { useToasts } from '../components/common/ErrorToast'
import type { FoodSafetyEvent } from '../types'

type SupabaseStatus = 'pending' | 'connected' | 'error'

export default function Dashboard() {
  const { events, error: realtimeError, connected } = useRealtimeEvents()
  const [selectedSite, setSelectedSite] = useState<string | null>(null)
  const [selectedDate, setSelectedDate] = useState(() => format(new Date(), 'yyyy-MM-dd'))
  const [selectedAlert, setSelectedAlert] = useState<FoodSafetyEvent | null>(null)
  const [supabaseStatus, setSupabaseStatus] = useState<SupabaseStatus>('pending')
  const { toasts, addToast, dismissToast } = useToasts()

  const sites = useSites(events)
  const stations = useDerivedStationState(events, selectedSite)
  const alerts = useAlerts(events, selectedSite)

  // WEB-2: connection test
  useEffect(() => {
    supabase
      .from('events')
      .select('id')
      .limit(1)
      .then(({ error }) => {
        setSupabaseStatus(error ? 'error' : 'connected')
      })
  }, [])

  // WEB-10: surface realtime errors as toasts
  useEffect(() => {
    if (realtimeError) addToast(realtimeError)
  }, [realtimeError])

  useEffect(() => {
    if (connected && realtimeError === null) {
      // Clear any connection-lost toast when we reconnect
    }
  }, [connected])

  // WEB-6: auto-select first site when sites load and none selected
  useEffect(() => {
    if (sites.length === 1 && selectedSite === null) {
      setSelectedSite(sites[0])
    }
  }, [sites])

  async function handleSeedDemo() {
    try {
      addToast('Seeding demo data…')
      await seedDemo()
      addToast('Demo data seeded! Realtime stream will update shortly.')
    } catch (err) {
      addToast(`Seed failed: ${(err as Error).message}`)
    }
  }

  return (
    <div className="flex flex-col h-screen overflow-hidden">
      <TopBar
        sites={sites}
        selectedSite={selectedSite}
        onSelectSite={setSelectedSite}
        supabaseStatus={supabaseStatus}
        events={events}
        selectedDate={selectedDate}
        onDateChange={setSelectedDate}
        onToast={addToast}
      />

      <div className="flex flex-col flex-1 overflow-hidden">
        {/* Multi-site rollup — WEB-9 */}
        {sites.length > 1 && (
          <div className="px-5 pt-4 shrink-0">
            <MultiSiteRollup
              sites={sites}
              stations={stations}
              alerts={alerts}
              onSelectSite={(s) => setSelectedSite(s)}
            />
          </div>
        )}

        {/* Main content: board + alerts feed */}
        <div className="flex flex-1 overflow-hidden gap-0">
          {/* Station board */}
          <div className="flex-1 overflow-y-auto p-5">
            <StationBoard stations={stations} onSeedDemo={handleSeedDemo} />
          </div>

          {/* Alerts feed — fixed width right panel */}
          <div className="w-80 shrink-0 border-l border-outline-variant overflow-hidden flex flex-col bg-surface-container">
            <AlertsFeed
              alerts={alerts}
              allEvents={events}
              onSelectAlert={setSelectedAlert}
              onSeedDemo={handleSeedDemo}
            />
          </div>
        </div>
      </div>

      {/* Alert detail modal */}
      {selectedAlert && (
        <AlertDetail
          alert={selectedAlert}
          allEvents={events}
          onClose={() => setSelectedAlert(null)}
        />
      )}

      {/* Error toasts */}
      <ErrorToast toasts={toasts} onDismiss={dismissToast} />
    </div>
  )
}
