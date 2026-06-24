import type { FoodSafetyEvent } from '../types'

const AI_BASE = (import.meta.env.VITE_AI_API_BASE as string) || ''

export interface S3PdfFile {
  key: string
  size: number
  last_modified: string
  site_id: string
  date: string
}

export async function listPdfs(siteId?: string): Promise<S3PdfFile[]> {
  const qs = siteId ? `?site_id=${encodeURIComponent(siteId)}` : ''
  const res = await fetch(`${AI_BASE}/pdf/list${qs}`)
  if (!res.ok) throw new Error(`list pdfs ${res.status}`)
  const data = await res.json()
  return data.items as S3PdfFile[]
}

export async function presignPdf(key: string): Promise<string> {
  const res = await fetch(`${AI_BASE}/pdf/url?key=${encodeURIComponent(key)}`)
  if (!res.ok) throw new Error(`presign ${res.status}`)
  const data = await res.json()
  return data.url
}

export async function narrateIncident(
  alert: FoodSafetyEvent,
  correctiveAction: Record<string, unknown>,
  tempSlice: FoodSafetyEvent[]
): Promise<string> {
  const res = await fetch(`${AI_BASE}/ai/narrate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ alert, corrective_action: correctiveAction, temp_slice: tempSlice }),
  })
  if (!res.ok) throw new Error(`narrate ${res.status}`)
  const data = await res.json()
  return data.narrative ?? data.text ?? JSON.stringify(data)
}

export async function uploadPdf(
  blob: Blob,
  siteId: string,
  date: string
): Promise<{ bucket: string; key: string }> {
  const form = new FormData()
  form.append('file', blob, `haccp-${siteId}-${date}.pdf`)
  form.append('site_id', siteId)
  form.append('date', date)
  const res = await fetch(`${AI_BASE}/pdf`, {
    method: 'POST',
    body: form,
  })
  if (!res.ok) throw new Error(`pdf upload ${res.status}`)
  return res.json()
}
