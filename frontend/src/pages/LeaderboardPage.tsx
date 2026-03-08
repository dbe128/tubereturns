import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchChannels, fetchChannelStats, triggerIngestion } from '../api/client'
import type { Channel, ChannelStats } from '../api/types'

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
    <div className="max-w-5xl mx-auto px-4 py-10">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">TubeReturns</h1>
          <p className="text-gray-500 mt-1">Finance YouTubers ranked by historical stock pick performance</p>
        </div>
        {!error && (
          <button
            onClick={handleIngest}
            disabled={ingesting}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm font-medium
                       hover:bg-primary-700 disabled:opacity-50 transition-colors"
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
        <div className="bg-danger-50 border border-danger-500 text-danger-600 rounded-lg p-4">
          {error}
        </div>
      )}

      {!loading && !error && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500 uppercase tracking-wider">
                <th className="px-6 py-3">#</th>
                <th className="px-6 py-3">Channel</th>
                <th className="px-6 py-3 text-right">Videos</th>
                <th className="px-6 py-3 text-right">Processed</th>
                <th className="px-6 py-3">Buy Picks</th>
                <th className="px-6 py-3">Sell Picks</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-gray-400">
                    No channels yet. Run ingestion to get started.
                  </td>
                </tr>
              ) : (
                rows.map((row, i) => (
                  <tr
                    key={row.youtubeChannelId}
                    className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
                  >
                    <td className="px-6 py-4 text-gray-400 font-mono text-sm">{i + 1}</td>
                    <td className="px-6 py-4">
                      <Link
                        to={`/channel/${row.youtubeChannelId}`}
                        className="font-semibold text-gray-900 hover:text-primary-600 transition-colors"
                      >
                        {row.channelName}
                      </Link>
                    </td>
                    <td className="px-6 py-4 text-right text-gray-600 font-mono text-sm">
                      {row.stats?.totalVideos ?? '—'}
                    </td>
                    <td className="px-6 py-4 text-right text-gray-600 font-mono text-sm">
                      {row.stats?.processedVideos ?? '—'}
                    </td>
                    <td className="px-6 py-4 text-sm text-green-700">
                      {row.stats && row.stats.buyPicks.length > 0
                        ? row.stats.buyPicks.join(', ')
                        : <span className="text-gray-300">—</span>}
                    </td>
                    <td className="px-6 py-4 text-sm text-red-600">
                      {row.stats && row.stats.sellPicks.length > 0
                        ? row.stats.sellPicks.join(', ')
                        : <span className="text-gray-300">—</span>}
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
