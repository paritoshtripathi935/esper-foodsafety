interface Props {
  title: string
  body: string
  onSeed?: () => void
}

export default function EmptyState({ title, body, onSeed }: Props) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
      <div className="w-12 h-12 rounded-full bg-surface-container-high flex items-center justify-center">
        <svg className="w-6 h-6 text-on-surface-variant" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2v-4M9 21H5a2 2 0 01-2-2v-4m0 0h18" />
        </svg>
      </div>
      <div>
        <p className="font-semibold text-on-surface">{title}</p>
        <p className="text-sm text-on-surface-variant mt-1 max-w-xs">{body}</p>
      </div>
      {onSeed && (
        <button
          onClick={onSeed}
          className="px-4 py-2 rounded-lg bg-primary text-on-primary text-sm font-semibold hover:bg-primary/90 transition-colors"
        >
          Seed demo data
        </button>
      )}
    </div>
  )
}
