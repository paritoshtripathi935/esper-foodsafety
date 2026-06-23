// DATA-4: Batch ingest Edge Function
// POST /functions/v1/ingest
// Body: FoodSafetyEvent[]  (see shared/schema/events.ts)
// Returns: { inserted: number }
//
// Deploy: supabase functions deploy ingest

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const REQUIRED_FIELDS = ['device_id', 'site_id', 'station', 'probe_id', 'type', 'value', 'ts'] as const
const VALID_TYPES = new Set(['temp', 'timer', 'corrective_action', 'alert'])

interface FoodSafetyEvent {
  id?: string
  device_id: string
  site_id: string
  station: string
  probe_id: string
  type: string
  value: number | string
  ts: string
  payload?: Record<string, unknown>
}

function validate(event: unknown): event is FoodSafetyEvent {
  if (!event || typeof event !== 'object') return false
  const e = event as Record<string, unknown>
  for (const field of REQUIRED_FIELDS) {
    if (e[field] === undefined || e[field] === null) return false
  }
  if (!VALID_TYPES.has(e.type as string)) return false
  return true
}

Deno.serve(async (req: Request) => {
  if (req.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'Method not allowed' }), {
      status: 405,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  let body: unknown
  try {
    body = await req.json()
  } catch {
    return new Response(JSON.stringify({ error: 'Invalid JSON body' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  if (!Array.isArray(body)) {
    return new Response(JSON.stringify({ error: 'Body must be a JSON array of events' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  const events = body as unknown[]
  const invalid: number[] = []
  const valid: FoodSafetyEvent[] = []

  for (let i = 0; i < events.length; i++) {
    if (validate(events[i])) {
      valid.push(events[i] as FoodSafetyEvent)
    } else {
      invalid.push(i)
    }
  }

  if (invalid.length > 0) {
    return new Response(
      JSON.stringify({ error: `Invalid events at indices: ${invalid.join(', ')}` }),
      { status: 400, headers: { 'Content-Type': 'application/json' } }
    )
  }

  if (valid.length === 0) {
    return new Response(JSON.stringify({ inserted: 0 }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  // Sort by ts ascending to preserve offline-replay order
  valid.sort((a, b) => new Date(a.ts).getTime() - new Date(b.ts).getTime())

  // Use service-role key (set as Supabase secret: supabase secrets set SUPABASE_SERVICE_ROLE_KEY=...)
  const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

  const supabase = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false },
  })

  const { error } = await supabase.from('events').insert(
    valid.map(({ id: _id, ...rest }) => rest)  // strip client-supplied id; DB assigns uuid
  )

  if (error) {
    console.error('Insert error:', error)
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  return new Response(JSON.stringify({ inserted: valid.length }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
})
