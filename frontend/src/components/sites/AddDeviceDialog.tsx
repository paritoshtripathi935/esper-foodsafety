import { useState } from 'react'
import { supabase } from '../../services/supabase'

function toSlug(name: string, prefix: string): string {
  const body = name
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return `${prefix}-${body}`
}

interface Props {
  siteId: string
  onClose: () => void
  onSuccess: (msg: string) => void
}

export default function AddDeviceDialog({ siteId, onClose, onSuccess }: Props) {
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const slug = name.trim() ? toSlug(name.trim(), 'device') : 'device-…'
  const canSubmit = name.trim().length > 0 && !loading

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    setLoading(true)
    setError(null)
    const { error: err } = await supabase
      .from('devices')
      .insert({
        id: toSlug(name.trim(), 'device'),
        site_id: siteId,
        name: name.trim(),
        kind: 'kiosk',
        last_seen: null,
      })
      .select()
      .single()
    setLoading(false)
    if (err) {
      setError(err.message)
    } else {
      onSuccess(`Device "${name.trim()}" added`)
      onClose()
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-md rounded-2xl bg-surface-container border border-outline-variant shadow-xl">
        <div className="p-6 flex flex-col gap-5">
          <div>
            <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-1">
              New Device
            </p>
            <h2 className="text-xl font-semibold text-on-surface">Add a device</h2>
          </div>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {/* Device name */}
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">
                Device Name
              </label>
              <input
                autoFocus
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Line Manager Tablet"
                disabled={loading}
                className="w-full rounded-lg border border-outline-variant bg-surface-container-high
                  px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant/50
                  focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-50"
              />
              <p className="font-mono text-[11px] text-on-surface-variant">ID: {slug}</p>
            </div>

            {error && (
              <p className="text-sm text-error">{error}</p>
            )}

            <div className="flex items-center justify-end gap-3 pt-1">
              <button
                type="button"
                onClick={onClose}
                disabled={loading}
                className="px-4 py-2 rounded-lg text-sm font-semibold text-on-surface-variant
                  hover:bg-surface-container-high transition-colors disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!canSubmit}
                className="px-4 py-2 rounded-lg text-sm font-semibold bg-primary-container text-on-primary
                  hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed
                  flex items-center gap-2"
              >
                {loading && (
                  <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                  </svg>
                )}
                {loading ? 'Adding…' : 'Add Device'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
