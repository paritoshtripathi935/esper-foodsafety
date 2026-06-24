import { useEffect, useRef, useState } from 'react'
import { supabase } from '../services/supabase'
import type { FoodSafetyEvent } from '../types'

const MAX_EVENTS = 500
const INITIAL_FETCH = 200

let eventsInstanceId = 0

export function useRealtimeEvents() {
  const [events, setEvents] = useState<FoodSafetyEvent[]>([])
  const [error, setError] = useState<string | null>(null)
  const [connected, setConnected] = useState(false)
  const channelRef = useRef<ReturnType<typeof supabase.channel> | null>(null)
  const channelName = useRef(`public:events-${++eventsInstanceId}`)

  useEffect(() => {
    let cancelled = false

    async function init() {
      // Initial fetch
      const { data, error: fetchErr } = await supabase
        .from('events')
        .select('*')
        .order('ts', { ascending: false })
        .limit(INITIAL_FETCH)

      if (cancelled) return

      if (fetchErr) {
        setError(fetchErr.message)
        return
      }

      setEvents((data as FoodSafetyEvent[]) ?? [])

      // Realtime subscription
      const channel = supabase
        .channel(channelName.current)
        .on(
          'postgres_changes',
          { event: 'INSERT', schema: 'public', table: 'events' },
          (payload) => {
            const newEvent = payload.new as FoodSafetyEvent
            setEvents((prev) => {
              const next = [newEvent, ...prev]
              return next.length > MAX_EVENTS ? next.slice(0, MAX_EVENTS) : next
            })
          }
        )
        .subscribe((status) => {
          if (status === 'SUBSCRIBED') setConnected(true)
          if (status === 'CLOSED' || status === 'CHANNEL_ERROR') {
            setConnected(false)
            setError('Realtime connection lost')
          }
          if (status === 'SUBSCRIBED') setError(null)
        })

      channelRef.current = channel
    }

    init()

    return () => {
      cancelled = true
      if (channelRef.current) {
        supabase.removeChannel(channelRef.current)
        channelRef.current = null
      }
    }
  }, [])

  return { events, error, connected }
}
