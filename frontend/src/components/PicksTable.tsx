import type { Pick } from '../api/types'
import { ReturnBadge } from './ReturnBadge'

interface Props {
  picks: Pick[]
}

export function PicksTable({ picks }: Props) {
  if (picks.length === 0) {
    return (
      <p className="text-gray-500 py-8 text-center">No picks found.</p>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-gray-200 text-left text-gray-500 text-xs uppercase tracking-wider">
            <th className="pb-2 pr-4">Ticker</th>
            <th className="pb-2 pr-4">Signal</th>
            <th className="pb-2 pr-4">Video</th>
            <th className="pb-2 pr-4 text-right">1d</th>
            <th className="pb-2 pr-4 text-right">7d</th>
            <th className="pb-2 pr-4 text-right">30d</th>
            <th className="pb-2 pr-4 text-right">90d</th>
            <th className="pb-2 text-right">1y</th>
          </tr>
        </thead>
        <tbody>
          {picks.map((pick) => (
            <tr
              key={pick.id}
              className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
            >
              <td className="py-3 pr-4">
                <div className="font-mono font-bold text-gray-900">{pick.tickerSymbol}</div>
                {pick.companyName && (
                  <div className="text-gray-400 text-xs">{pick.companyName}</div>
                )}
              </td>
              <td className="py-3 pr-4">
                <SignalBadge signal={pick.signal} />
              </td>
              <td className="py-3 pr-4 max-w-xs">
                <a
                  href={`https://www.youtube.com/watch?v=${pick.videoId}`}
                  target="_blank"
                  rel="noreferrer"
                  className="text-primary-600 hover:underline truncate block"
                  title={pick.videoTitle ?? undefined}
                >
                  {pick.videoTitle ?? pick.videoId}
                </a>
                <div className="text-gray-400 text-xs">
                  {new Date(pick.extractionTimestamp).toLocaleDateString()}
                </div>
              </td>
              <td className="py-3 pr-4 text-right">
                <ReturnBadge value={pick.performance?.return1d} />
              </td>
              <td className="py-3 pr-4 text-right">
                <ReturnBadge value={pick.performance?.return7d} />
              </td>
              <td className="py-3 pr-4 text-right">
                <ReturnBadge value={pick.performance?.return30d} />
              </td>
              <td className="py-3 pr-4 text-right">
                <ReturnBadge value={pick.performance?.return90d} />
              </td>
              <td className="py-3 text-right">
                <ReturnBadge value={pick.performance?.return1y} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function SignalBadge({ signal }: { signal: 'BUY' | 'SELL' }) {
  const isBuy = signal === 'BUY'
  return (
    <span
      className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
        isBuy
          ? 'bg-success-50 text-success-600'
          : 'bg-danger-50 text-danger-600'
      }`}
    >
      {signal}
    </span>
  )
}
