import { pdf } from '@react-pdf/renderer'
import type { ReactElement } from 'react'

export async function renderPdfBlob(element: ReactElement): Promise<Blob> {
  return pdf(element).toBlob()
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
