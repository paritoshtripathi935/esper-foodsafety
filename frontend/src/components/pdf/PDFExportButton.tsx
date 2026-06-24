import { useState } from 'react'
import { pdf } from '@react-pdf/renderer'
import type { FoodSafetyEvent } from '../../types'
import HACCPReport from './HACCPReport'
import { uploadPdf } from '../../services/ai'

interface Props {
  events: FoodSafetyEvent[]
  siteId: string
  date: string
  onToast?: (msg: string) => void
}

export default function PDFExportButton({ events, siteId, date, onToast }: Props) {
  const [loading, setLoading] = useState(false)

  async function handleExport() {
    setLoading(true)
    try {
      const blob = await pdf(
        <HACCPReport events={events} siteId={siteId} date={date} />
      ).toBlob()

      // Local download
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `haccp-${siteId}-${date}.pdf`
      a.click()
      URL.revokeObjectURL(url)

      // Upload to EC2 → S3
      try {
        const result = await uploadPdf(blob, siteId, date)
        onToast?.(`Saved to S3: ${result.bucket}/${result.key}`)
      } catch (err) {
        onToast?.(`Downloaded locally (S3 upload failed: ${(err as Error).message})`)
      }
    } catch (err) {
      onToast?.(`PDF generation failed: ${(err as Error).message}`)
    } finally {
      setLoading(false)
    }
  }

  return (
    <button
      onClick={handleExport}
      disabled={loading}
      className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-primary-container text-on-primary
        text-sm font-semibold hover:bg-primary/80 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
    >
      {loading ? (
        <div className="w-4 h-4 border-2 border-on-primary border-t-transparent rounded-full animate-spin" />
      ) : (
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
      )}
      Export PDF
    </button>
  )
}
