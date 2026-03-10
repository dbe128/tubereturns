import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchChannels, fetchChannelStats, triggerIngestion } from '../api/client'
import type { Channel, ChannelStats } from '../api/types'
import { useBackendRecovery } from '../hooks/useBackendRecovery'
import { SpyChart } from '../components/SpyChart'

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

  useBackendRecovery(error !== null, load)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const channels = await fetchChannels()
      const rowsWithStats = await Promise.all(
        channels.map(async (ch) => {
          try {
            const stats = await fetchChannelStats(ch.youtubeChannelId)
            return { ...ch, stats }
          } catch {
            return { ...ch, stats: null }
          }
        })
      )
      setRows(rowsWithStats)
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
    <div className="max-w-screen-2xl mx-auto px-6 py-10">
      <div className="flex items-center justify-between mb-8">
        <div>
          <p className="text-gray-500 text-sm">Finance YouTubers ranked by historical stock pick performance</p>
        </div>
        {!error && (
          <button
            onClick={handleIngest}
            disabled={ingesting}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm font-semibold
                       hover:bg-primary-700 disabled:opacity-50 transition-colors shadow-sm"
          >
            {ingesting ? 'Running…' : 'Run Ingestion'}
          </button>
        )}
      </div>

      {loading && (
        <div className="flex justify-center py-20">
          <div className="animate-spin rounded-full h-8 w-8 border-2 border-primary-500 border-t-transparent" />
        </div>
      )}

      {error && (
        <div className="bg-danger-50 border border-danger-500 text-danger-500 rounded-xl p-4 text-sm">
          {error}
        </div>
      )}

      {!loading && !error && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-200 text-left text-xs font-semibold text-gray-400 uppercase tracking-wider">
                <th className="px-6 py-4">#</th>
                <th className="px-6 py-4">Channel</th>
                <th className="px-6 py-4 text-right">Videos</th>
                <th className="px-6 py-4 text-right">Processed</th>
                <th className="px-6 py-4">
                  <span className="text-primary-600">▲</span> Buy Picks
                </th>
                <th className="px-6 py-4">
                  <span className="text-danger-500">▼</span> Sell Picks
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-16 text-center text-gray-400 text-sm">
                    No channels yet. Run ingestion to get started.
                  </td>
                </tr>
              ) : (
                rows.map((row, i) => (
                  <tr
                    key={row.youtubeChannelId}
                    className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
                  >
                    <td className="px-6 py-4 text-gray-300 font-mono text-sm">{i + 1}</td>
                    <td className="px-6 py-4">
                      <Link
                        to={`/channel/${row.youtubeChannelId}`}
                        className="flex items-center gap-3 group"
                      >
                        {row.hasThumbnail ? (
                          <img
                            src={`/api/channels/${row.youtubeChannelId}/thumbnail`}
                            alt={row.channelName}
                            className="w-9 h-9 rounded-full object-cover flex-shrink-0 ring-2 ring-gray-100"
                          />
                        ) : (
                          <div className="w-9 h-9 rounded-full bg-gray-100 flex-shrink-0" />
                        )}
                        <span className="font-semibold text-gray-900 group-hover:text-primary-600 transition-colors">
                          {row.channelName}
                        </span>
                      </Link>
                    </td>
                    <td className="px-6 py-4 text-right text-gray-500 font-mono text-sm">
                      {row.stats?.totalVideos ?? '—'}
                    </td>
                    <td className="px-6 py-4 text-right text-gray-500 font-mono text-sm">
                      {row.stats?.processedVideos ?? '—'}
                    </td>
                    <td className="px-6 py-4 text-sm font-mono text-primary-600 font-medium">
                      {row.stats && row.stats.buyPicks.length > 0
                        ? row.stats.buyPicks.join(', ')
                        : <span className="text-gray-200 font-normal">—</span>}
                    </td>
                    <td className="px-6 py-4 text-sm font-mono text-danger-500 font-medium">
                      {row.stats && row.stats.sellPicks.length > 0
                        ? row.stats.sellPicks.join(', ')
                        : <span className="text-gray-200 font-normal">—</span>}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {!error && <SpyChart />}
    </div>
  )
}
