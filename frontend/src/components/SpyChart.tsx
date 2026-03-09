import { useEffect, useState } from 'react'
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'
import { fetchStockPrices, type PricePoint } from '../api/client'

type Timeframe = '1W' | '1M' | 'YTD' | '1Y' | '2Y' | '3Y' | '5Y' | '10Y'

const TIMEFRAMES: Timeframe[] = ['1W', '1M', 'YTD', '1Y', '2Y', '3Y', '5Y', '10Y']

function fromDate(tf: Timeframe): string {
  const now = new Date()
  switch (tf) {
    case '1W':  now.setDate(now.getDate() - 7); break
    case '1M':  now.setMonth(now.getMonth() - 1); break
    case 'YTD': return `${new Date().getFullYear()}-01-01`
    case '1Y':  now.setFullYear(now.getFullYear() - 1); break
    case '2Y':  now.setFullYear(now.getFullYear() - 2); break
    case '3Y':  now.setFullYear(now.getFullYear() - 3); break
    case '5Y':  now.setFullYear(now.getFullYear() - 5); break
    case '10Y': now.setFullYear(now.getFullYear() - 10); break
  }
  return now.toISOString().slice(0, 10)
}

function toDate(): string {
  return new Date().toISOString().slice(0, 10)
}

function formatXAxis(dateStr: string, tf: Timeframe): string {
  const d = new Date(dateStr)
  if (tf === '1W' || tf === '1M') {
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  }
  return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short' })
}

export function SpyChart() {
  const [timeframe, setTimeframe] = useState<Timeframe>('1Y')
  const [data, setData] = useState<PricePoint[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    fetchStockPrices('SPY', fromDate(timeframe), toDate())
      .then(setData)
      .catch(() => setData([]))
      .finally(() => setLoading(false))
  }, [timeframe])

  const first = data[0]?.close ?? 0
  const last = data[data.length - 1]?.close ?? 0
  const change = first > 0 ? ((last - first) / first) * 100 : 0
  const positive = change >= 0
  const color = positive ? '#2d7a2d' : '#cc1a1a'

  const thinned = data.filter((_, i) => {
    if (data.length <= 60) return true
    if (data.length <= 260) return i % 2 === 0
    return i % Math.ceil(data.length / 200) === 0
  })

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mt-6">
      <div className="flex items-start justify-between mb-4 gap-4 flex-wrap">
        <div>
          <div className="flex items-baseline gap-3">
            <h2 className="text-base font-semibold text-gray-800">S&amp;P 500 (SPY)</h2>
            {!loading && data.length > 0 && (
              <>
                <span className="text-xl font-bold text-gray-900">${last.toFixed(2)}</span>
                <span className={`text-sm font-semibold ${positive ? 'text-primary-600' : 'text-danger-500'}`}>
                  {positive ? '+' : ''}{change.toFixed(2)}%
                </span>
              </>
            )}
          </div>
          <p className="text-xs text-gray-400 mt-0.5">Historical closing prices</p>
        </div>
        <div className="flex gap-1">
          {TIMEFRAMES.map((tf) => (
            <button
              key={tf}
              onClick={() => setTimeframe(tf)}
              className={`px-3 py-1 text-xs rounded-lg font-semibold transition-colors ${
                timeframe === tf
                  ? 'bg-primary-600 text-white'
                  : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
              }`}
            >
              {tf}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center items-center h-56">
          <div className="animate-spin rounded-full h-7 w-7 border-2 border-primary-500 border-t-transparent" />
        </div>
      ) : data.length === 0 ? (
        <div className="flex justify-center items-center h-56 text-gray-400 text-sm">
          No data available for this timeframe yet.
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={260}>
          <AreaChart data={thinned} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="spyGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor={color} stopOpacity={0.15} />
                <stop offset="95%" stopColor={color} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
            <XAxis
              dataKey="date"
              tickFormatter={(v) => formatXAxis(v, timeframe)}
              tick={{ fontSize: 11, fill: '#9ca3af' }}
              tickLine={false}
              axisLine={false}
              interval="preserveStartEnd"
            />
            <YAxis
              domain={['auto', 'auto']}
              tick={{ fontSize: 11, fill: '#9ca3af' }}
              tickLine={false}
              axisLine={false}
              tickFormatter={(v) => `$${v.toFixed(0)}`}
              width={55}
            />
            <Tooltip
              contentStyle={{ fontSize: 12, borderRadius: 8, border: '1px solid #e5e7eb', boxShadow: '0 4px 12px rgba(0,0,0,0.08)' }}
              formatter={(v: number) => [`$${v.toFixed(2)}`, 'SPY']}
              labelFormatter={(l) => new Date(l).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })}
            />
            <Area
              type="monotone"
              dataKey="close"
              stroke={color}
              strokeWidth={2}
              fill="url(#spyGrad)"
              dot={false}
              activeDot={{ r: 4, fill: color }}
            />
          </AreaChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
