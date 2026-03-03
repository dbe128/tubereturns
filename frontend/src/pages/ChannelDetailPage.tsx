import { useEffect, useState, type ReactNode } from 'react'
import { useParams, Link } from 'react-router-dom'
import { fetchChannel, fetchChannelStats, fetchPicksForChannel } from '../api/client'
import type { Channel, ChannelStats, Pick } from '../api/types'
import { ReturnBadge } from '../components/ReturnBadge'
import { PicksTable } from '../components/PicksTable'
import { PerformanceChart } from '../components/PerformanceChart'

export function ChannelDetailPage() {
  const { channelId } = useParams<{ channelId: string }>()
  const [channel, setChannel] = useState<Channel | null>(null)
  const [stats, setStats] = useState<ChannelStats | null>(null)
  const [picks, setPicks] = useState<Pick[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<'ALL' | 'BUY' | 'SELL'>('ALL')

  useEffect(() => {
    if (!channelId) return
    load(channelId)
  }, [channelId])

  async function load(id: string) {
    setLoading(true)
    setError(null)
    try {
      const [ch, st, pk] = await Promise.all([
        fetchChannel(id),
        fetchChannelStats(id).catch(() => null),
        fetchPicksForChannel(id),
      ])
      setChannel(ch)
      setStats(st)
      setPicks(pk)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  const filteredPicks = picks.filter(
    (p) => filter === 'ALL' || p.signal === filter
  )

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <div className="animate-spin rounded-full h-8 w-8 border-2 border-primary-500 border-t-transparent" />
      </div>
    )
  }

  if (error || !channel) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-10">
        <div className="bg-danger-50 border border-danger-500 text-danger-600 rounded-lg p-4">
          {error ?? 'Channel not found'}
        </div>
        <Link to="/" className="mt-4 inline-block text-primary-600 hover:underline text-sm">
          ← Back to leaderboard
        </Link>
      </div>
    )
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-10">
      <Link to="/" className="text-primary-600 hover:underline text-sm mb-6 inline-block">
        ← Leaderboard
      </Link>

      {/* Channel header */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{channel.channelName}</h1>
            {channel.description && (
              <p className="text-gray-500 mt-1 text-sm">{channel.description}</p>
            )}
            {channel.channelUrl && (
              <a
                href={channel.channelUrl}
                target="_blank"
                rel="noreferrer"
                className="text-primary-600 hover:underline text-sm mt-2 inline-block"
              >
                YouTube Channel ↗
              </a>
            )}
          </div>
        </div>

        {/* Stats row */}
        <div className="mt-6 grid grid-cols-2 sm:grid-cols-4 gap-4">
          <Stat label="Total Picks" value={stats?.totalPicks ?? picks.length} />
          <Stat label="Avg 30d Return" value={<ReturnBadge value={stats?.avgReturn30d} />} />
          <Stat
            label="Subscribers"
            value={
              channel.subscriberCount != null
                ? formatNum(channel.subscriberCount)
                : '—'
            }
          />
          <Stat label="Videos" value={channel.videoCount ?? '—'} />
        </div>
      </div>

      {/* Performance chart */}
      {picks.length > 0 && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
          <h2 className="text-base font-semibold text-gray-800 mb-4">
            30-Day Return by Pick (top 15)
          </h2>
          <PerformanceChart picks={picks} />
        </div>
      )}

      {/* Picks table */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-base font-semibold text-gray-800">Stock Picks</h2>
          <div className="flex gap-1">
            {(['ALL', 'BUY', 'SELL'] as const).map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={`px-3 py-1 text-xs rounded-full font-medium transition-colors ${
                  filter === f
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {f}
              </button>
            ))}
          </div>
        </div>
        <PicksTable picks={filteredPicks} />
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="bg-gray-50 rounded-lg px-4 py-3">
      <div className="text-xs text-gray-500 mb-1">{label}</div>
      <div className="font-semibold text-gray-900">{value}</div>
    </div>
  )
}

function formatNum(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(0)}K`
  return String(n)
}
