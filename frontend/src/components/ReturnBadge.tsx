interface Props {
  value: number | null | undefined
  suffix?: string
}

export function ReturnBadge({ value, suffix = '%' }: Props) {
  if (value == null) {
    return <span className="text-gray-400 text-sm">—</span>
  }

  const pct = (value * 100).toFixed(2)
  const positive = value >= 0

  return (
    <span
      className={`inline-flex items-center gap-0.5 font-mono text-sm font-semibold ${
        positive ? 'text-success-600' : 'text-danger-600'
      }`}
    >
      {positive ? '+' : ''}
      {pct}
      {suffix}
    </span>
  )
}
