import { useEffect, useState, useCallback } from 'react'
import { Command } from 'cmdk'
import { useNavigate } from 'react-router-dom'
import type { Site, Device } from '../../hooks/useRegistry'

interface Props {
  sites: Site[]
  devices: Device[]
  onSeedDemo: () => Promise<void>
}

const NAV_ITEMS = [
  { label: 'Overview',  to: '/',        icon: '🏠' },
  { label: 'Sites',     to: '/sites',   icon: '🏢' },
  { label: 'Audit Log', to: '/audit',   icon: '📋' },
  { label: 'Ask AI',    to: '/ask',     icon: '🤖' },
  { label: 'Reports',   to: '/reports', icon: '📄' },
]

export default function CommandPalette({ sites, devices, onSeedDemo }: Props) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const navigate = useNavigate()

  // ── global ⌘K / Ctrl+K listener ─────────────────────────────────────────
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        setOpen((v) => !v)
      }
    }
    function onOpen() { setOpen(true) }
    window.addEventListener('keydown', onKey)
    window.addEventListener('open-command-palette', onOpen)
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('open-command-palette', onOpen)
    }
  }, [])

  const close = useCallback(() => {
    setOpen(false)
    setQuery('')
  }, [])

  function go(to: string) {
    navigate(to)
    close()
  }

  // "ask <question>" shortcut — navigate to /ask with pre-fill param
  const isAskQuery = query.toLowerCase().startsWith('ask ')
  const askQuestion = isAskQuery ? query.slice(4).trim() : ''

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center pt-[12vh]"
      onMouseDown={(e) => { if (e.target === e.currentTarget) close() }}
    >
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" />

      {/* Palette */}
      <div className="relative w-full max-w-[520px] mx-4">
        <Command
          className="rounded-2xl border border-outline-variant bg-surface-container shadow-2xl overflow-hidden"
          loop
          onKeyDown={(e) => { if (e.key === 'Escape') close() }}
        >
          <div className="flex items-center gap-2 px-4 py-3 border-b border-outline-variant">
            <svg className="w-4 h-4 shrink-0 text-on-surface-variant" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z" />
            </svg>
            <Command.Input
              autoFocus
              value={query}
              onValueChange={setQuery}
              placeholder="Go to… or type 'ask <question>'"
              className="flex-1 bg-transparent text-sm text-on-surface placeholder:text-on-surface-variant/60
                focus:outline-none"
            />
            <kbd className="shrink-0 px-1.5 py-0.5 rounded text-[10px] font-mono font-semibold
              bg-surface-container-high text-on-surface-variant border border-outline-variant">
              Esc
            </kbd>
          </div>

          <Command.List className="max-h-[400px] overflow-y-auto py-2 [&_[cmdk-group-heading]]:px-4 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-[10px] [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:uppercase [&_[cmdk-group-heading]]:tracking-widest [&_[cmdk-group-heading]]:text-on-surface-variant">

            <Command.Empty className="px-4 py-8 text-center text-sm text-on-surface-variant">
              No results for &ldquo;{query}&rdquo;
            </Command.Empty>

            {/* "ask <question>" shortcut */}
            {isAskQuery && askQuestion && (
              <Command.Group heading="Ask AI">
                <CommandItem
                  icon="🤖"
                  label={`Ask: "${askQuestion}"`}
                  onSelect={() => go(`/ask?q=${encodeURIComponent(askQuestion)}`)}
                />
              </Command.Group>
            )}

            {/* Navigation */}
            <Command.Group heading="Navigation">
              {NAV_ITEMS.map((item) => (
                <CommandItem
                  key={item.to}
                  icon={item.icon}
                  label={item.label}
                  onSelect={() => go(item.to)}
                />
              ))}
            </Command.Group>

            {/* Sites */}
            {sites.length > 0 && (
              <Command.Group heading="Sites">
                {sites.map((site) => (
                  <CommandItem
                    key={site.id}
                    icon="🏢"
                    label={site.name ?? site.id}
                    sub={site.address ?? undefined}
                    onSelect={() => go(`/sites/${site.id}`)}
                  />
                ))}
              </Command.Group>
            )}

            {/* Devices */}
            {devices.length > 0 && (
              <Command.Group heading="Devices">
                {devices.map((device) => {
                  const site = sites.find((s) => s.id === device.site_id)
                  return (
                    <CommandItem
                      key={device.id}
                      icon={device.kind === 'kiosk' ? '📱' : '🔌'}
                      label={device.name ?? device.id}
                      sub={site?.name ?? undefined}
                      onSelect={() => go(`/sites/${device.site_id}/devices/${device.id}`)}
                    />
                  )
                })}
              </Command.Group>
            )}

            {/* Actions */}
            <Command.Group heading="Actions">
              <CommandItem
                icon="📤"
                label="Export today's PDF"
                onSelect={() => { go('/reports') }}
              />
              <CommandItem
                icon="🌱"
                label="Seed demo data"
                onSelect={async () => { close(); await onSeedDemo() }}
              />
            </Command.Group>

          </Command.List>
        </Command>
      </div>
    </div>
  )
}

// ── shared item component ─────────────────────────────────────────────────────

interface ItemProps {
  icon: string
  label: string
  sub?: string
  onSelect: () => void
}

function CommandItem({ icon, label, sub, onSelect }: ItemProps) {
  return (
    <Command.Item
      value={label || ' '}
      onSelect={onSelect}
      className="flex items-center gap-3 mx-2 px-3 py-2 rounded-lg text-sm cursor-pointer
        text-on-surface aria-selected:bg-primary/10 aria-selected:text-primary
        hover:bg-surface-container-high transition-colors"
    >
      <span className="text-base shrink-0">{icon}</span>
      <span className="flex-1 min-w-0">
        <span className="truncate block">{label}</span>
        {sub && <span className="text-xs text-on-surface-variant truncate block">{sub}</span>}
      </span>
    </Command.Item>
  )
}
