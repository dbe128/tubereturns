import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts'
import type { Pick } from '../api/types'

interface Props {
  picks: Pick[]
}

export function PerformanceChart({ picks }: Props) {
  const data = picks
    .filter((p) => p.performance?.return30d != null)
    .map((p) => ({
      ticker: p.tickerSymbol,
      return30d: parseFloat(((p.performance!.return30d ?? 0) * 100).toFixed(2)),
      signal: p.signal,
    }))
    .sort((a, b) => b.return30d - a.return30d)
    .slice(0, 15)

  if (data.length === 0) {
    return <p className="text-gray-400 text-sm text-center py-8">No performance data yet.</p>
  }

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={data} margin={{ top: 4, right: 8, left: 0, bottom: 4 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
        <XAxis dataKey="ticker" tick={{ fontSize: 11 }} />
        <YAxis
          tickFormatter={(v) => `${v}%`}
          tick={{ fontSize: 11 }}
          width={48}
        />
        <Tooltip
          formatter={(value: number) => [`${value}%`, '30d Return']}
          labelStyle={{ fontWeight: 600 }}
        />
        <Bar dataKey="return30d" radius={[3, 3, 0, 0]}>
          {data.map((entry, i) => (
            <Cell
              key={i}
              fill={entry.return30d >= 0 ? '#16a34a' : '#dc2626'}
              fillOpacity={0.85}
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}
