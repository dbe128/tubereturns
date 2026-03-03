import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchChannels, fetchChannelStats, triggerIngestion } from '../api/client'
import type { Channel, ChannelStats } from '../api/types'
import { ReturnBadge } from '../components/ReturnBadge'

interface ChannelRow extends Channel {
  stats: ChannelStats | null
}

export function LeaderboardPage() {
  const [rows, setRows] = useState<ChannelRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [ingesting, setIngesting] = useState(false)

  useEffect(() => {
    load()
  }, [])

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const channels = await fetchChannels()
      const rowsWithStats = await Promise.all(
        channels.map(async (ch) => {
          try {
            const stats = await fetchChannelStats(ch.channelId)
            return { ...ch, stats }
          } catch {
            return { ...ch, stats: null }
          }
        })
      )
      const sorted = rowsWithStats.sort((a, b) => {
        const aR = a.stats?.avgReturn30d ?? -Infinity
        const bR = b.stats?.avgReturn30d ?? -Infinity
        return bR - aR
      })
      setRows(sorted)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  async function handleIngest() {
    setIngesting(true)
    try {
      await triggerIngestion()
      await load()
    } finally {
      setIngesting(false)
    }
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-10">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">TubeReturns</h1>
          <p className="text-gray-500 mt-1">Finance YouTubers ranked by historical stock pick performance</p>
        </div>
        <button
          onClick={handleIngest}
          disabled={ingesting}
          className="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm font-medium
                     hover:bg-primary-700 disabled:opacity-50 transition-colors"
        >
          {ingesting ? 'Running…' : 'Run Ingestion'}
        </button>
      </div>

      {loading && (
        <div className="flex justify-center py-20">
          <div className="animate-spin rounded-full h-8 w-8 border-2 border-primary-500 border-t-transparent" />
        </div>
      )}

      {error && (
        <div className="bg-danger-50 border border-danger-500 text-danger-600 rounded-lg p-4">
          {error}
        </div>
      )}

      {!loading && !error && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500 uppercase tracking-wider">
                <th className="px-6 py-3 w-10">#</th>
                <th className="px-6 py-3">Channel</th>
                <th className="px-6 py-3 text-right">Picks</th>
                <th className="px-6 py-3 text-right">Avg 30d Return</th>
                <th className="px-6 py-3 text-right">Subscribers</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-gray-400">
                    No channels yet. Run ingestion to get started.
                  </td>
                </tr>
              ) : (
                rows.map((row, i) => (
                  <tr
                    key={row.channelId}
                    className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
                  >
                    <td className="px-6 py-4 text-gray-400 font-mono text-sm">{i + 1}</td>
                    <td className="px-6 py-4">
                      <Link
                        to={`/channel/${row.channelId}`}
                        className="font-semibold text-gray-900 hover:text-primary-600 transition-colors"
                      >
                        {row.channelName}
                      </Link>
                      {row.description && (
                        <p className="text-gray-400 text-xs mt-0.5 truncate max-w-sm">
                          {row.description}
                        </p>
                      )}
                    </td>
                    <td className="px-6 py-4 text-right text-gray-600 font-mono text-sm">
                      {row.stats?.totalPicks ?? '—'}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <ReturnBadge value={row.stats?.avgReturn30d} />
                    </td>
                    <td className="px-6 py-4 text-right text-gray-500 text-sm">
                      {row.subscriberCount != null
                        ? formatNum(row.subscriberCount)
                        : '—'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function formatNum(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(0)}K`
  return String(n)
}
