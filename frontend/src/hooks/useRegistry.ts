import { useEffect, useRef, useState } from 'react'
import { supabase } from '../services/supabase'

let registryInstanceId = 0

export interface Site {
  id: string
  name: string
  address: string | null
  timezone: string
}

export interface Device {
  id: string
  site_id: string
  name: string
  kind: 'kiosk' | 'probe-sim'
  last_seen: string | null
}

export interface Station {
  id: string
  site_id: string
  slug: string
  name: string
  zone: 'cold' | 'hot' | 'frozen' | 'prep'
  max_temp_f: number
  min_temp_f: number | null
}

export function useRegistry() {
  const [sites, setSites] = useState<Site[]>([])
  const [devices, setDevices] = useState<Device[]>([])
  const [stations, setStations] = useState<Station[]>([])
  const channelName = useRef(`registry-${++registryInstanceId}`)

  useEffect(() => {
    supabase.from('sites').select('*').order('name').then(({ data }) => data && setSites(data))
    supabase.from('devices').select('*').then(({ data }) => data && setDevices(data))
    supabase.from('stations').select('*').then(({ data }) => data && setStations(data))

    const channel = supabase
      .channel(channelName.current)
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'devices' },
        () => supabase.from('devices').select('*').then(({ data }) => data && setDevices(data))
      )
      .subscribe()

    return () => {
      supabase.removeChannel(channel)
    }
  }, [])

  return { sites, devices, stations }
}
