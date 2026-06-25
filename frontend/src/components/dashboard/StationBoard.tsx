import { NavLink } from 'react-router-dom'
import type { StationState } from '../../types'
import TempCard from './TempCard'
import EmptyState from '../common/EmptyState'

interface Props {
  stations: StationState[]
  onSeedDemo?: () => void
}

export default function StationBoard({ stations, onSeedDemo }: Props) {
  if (stations.length === 0) {
    return (
      <div className="flex h-full items-center justify-center">
        <EmptyState
          title="No stations yet"
          body="Connect a kiosk probe or seed demo data to see live readings."
          onSeed={onSeedDemo}
        />
      </div>
    )
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4">
      {stations.map((s) => (
        <NavLink
          key={`${s.site_id}::${s.station}`}
          to={`/sites/${s.site_id}/stations/${s.station}`}
          className="block rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          <TempCard state={s} />
        </NavLink>
      ))}
    </div>
  )
}
