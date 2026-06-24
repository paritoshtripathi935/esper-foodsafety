import { useOutletContext } from 'react-router-dom'
import { useDerivedStationState } from '../hooks/useDerivedStationState'
import MultiSiteRollup from '../components/common/MultiSiteRollup'
import StationBoard from '../components/dashboard/StationBoard'
import type { FoodSafetyEvent, Site } from '../types'
import type { Station } from '../hooks/useRegistry'

interface ShellContext {
  events: FoodSafetyEvent[]
  sites: Site[]
  stationRegistry: Station[]
  selectedSite: string | null
  setSelectedSite: (site: string | null) => void
  addToast: (msg: string) => void
  handleSeedDemo: () => Promise<void>
}

export default function Overview() {
  const { events, sites, stationRegistry, selectedSite, setSelectedSite, handleSeedDemo } =
    useOutletContext<ShellContext>()

  const stations = useDerivedStationState(events, selectedSite, stationRegistry)

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {sites.length > 1 && (
        <div className="px-5 pt-4 shrink-0">
          <MultiSiteRollup
            sites={sites}
            stations={stations}
            alerts={[]}
            onSelectSite={setSelectedSite}
          />
        </div>
      )}

      <div className="flex-1 overflow-y-auto p-5">
        <StationBoard stations={stations} onSeedDemo={handleSeedDemo} />
      </div>
    </div>
  )
}
