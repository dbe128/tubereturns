import { useEffect, useMemo, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { fetchChannel, fetchChannelStats, fetchVideosForChannel } from '../api/client'
import type { Channel, ChannelStats, VideoSummary } from '../api/types'
import { useBackendRecovery } from '../hooks/useBackendRecovery'

type SortKey = 'index' | 'publishedAt' | 'transcriptStatus' | 'processingStatus'
type SortDir = 'asc' | 'desc'

const TRANSCRIPT_STATUSES: VideoSummary['transcriptStatus'][] = ['DOWNLOADED', 'NO_TRANSCRIPT', 'FAILED', 'PENDING']
const PROCESSING_STATUSES: VideoSummary['processingStatus'][] = ['COMPLETED', 'PROCESSING', 'FAILED', 'PENDING']

const TRANSCRIPT_ORDER: Record<VideoSummary['transcriptStatus'], number> = {
  DOWNLOADED: 0, NO_TRANSCRIPT: 1, PENDING: 2, FAILED: 3,
}
const PROCESSING_ORDER: Record<VideoSummary['processingStatus'], number> = {
  COMPLETED: 0, PROCESSING: 1, PENDING: 2, FAILED: 3,
}

export function ChannelDetailPage() {
  const { channelId } = useParams<{ channelId: string }>()
  const [channel, setChannel] = useState<Channel | null>(null)
  const [stats, setStats] = useState<ChannelStats | null>(null)
  const [videos, setVideos] = useState<VideoSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [sortKey, setSortKey] = useState<SortKey>('publishedAt')
  const [sortDir, setSortDir] = useState<SortDir>('desc')
  const [filterTranscript, setFilterTranscript] = useState<VideoSummary['transcriptStatus'] | ''>('')
  const [filterProcessing, setFilterProcessing] = useState<VideoSummary['processingStatus'] | ''>('')
  const [filterPick, setFilterPick] = useState('')

  useEffect(() => {
    if (!channelId) return
    load(channelId)
  }, [channelId])

  useBackendRecovery(error !== null, () => channelId && load(channelId))

  async function load(id: string) {
    setLoading(true)
    setError(null)
    try {
      const [ch, st, vids] = await Promise.all([
        fetchChannel(id),
        fetchChannelStats(id).catch(() => null),
        fetchVideosForChannel(id),
      ])
      setChannel(ch)
      setStats(st)
      setVideos(vids)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  function toggleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDir('asc')
    }
  }

  const allPicks = useMemo(() => {
    const tickers = new Set<string>()
    videos.forEach((v) => {
      v.buyPicks.forEach((t) => tickers.add(t))
      v.sellPicks.forEach((t) => tickers.add(t))
    })
    return Array.from(tickers).sort()
  }, [videos])

  const filtered = useMemo(() => {
    return videos.filter((v) => {
      if (filterTranscript && v.transcriptStatus !== filterTranscript) return false
      if (filterProcessing && v.processingStatus !== filterProcessing) return false
      if (filterPick && !v.buyPicks.includes(filterPick) && !v.sellPicks.includes(filterPick)) return false
      return true
    })
  }, [videos, filterTranscript, filterProcessing, filterPick])

  const sorted = useMemo(() => {
    const withIndex = filtered.map((v, i) => ({ v, originalIndex: videos.indexOf(v) + 1 }))
    withIndex.sort((a, b) => {
      let cmp = 0
      if (sortKey === 'index') {
        cmp = a.originalIndex - b.originalIndex
      } else if (sortKey === 'publishedAt') {
        cmp = new Date(a.v.publishedAt).getTime() - new Date(b.v.publishedAt).getTime()
      } else if (sortKey === 'transcriptStatus') {
        cmp = TRANSCRIPT_ORDER[a.v.transcriptStatus] - TRANSCRIPT_ORDER[b.v.transcriptStatus]
      } else if (sortKey === 'processingStatus') {
        cmp = PROCESSING_ORDER[a.v.processingStatus] - PROCESSING_ORDER[b.v.processingStatus]
      }
      return sortDir === 'asc' ? cmp : -cmp
    })
    return withIndex
  }, [filtered, sortKey, sortDir, videos])

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
    <div className="max-w-screen-2xl mx-auto px-6 py-10">
      <Link to="/" className="text-primary-600 hover:underline text-sm mb-6 inline-block">
        ← Leaderboard
      </Link>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{channel.channelName}</h1>
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
          <div className="flex gap-6 text-sm text-gray-500">
            <div className="text-center">
              <div className="text-2xl font-bold text-gray-900">{stats?.totalVideos ?? '—'}</div>
              <div>Videos</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-gray-900">{stats?.processedVideos ?? '—'}</div>
              <div>Processed</div>
            </div>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white border border-gray-200 rounded-xl shadow-sm px-5 py-4 mb-4">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Filters</span>
          {(filterTranscript || filterProcessing || filterPick) && (
            <button
              onClick={() => { setFilterTranscript(''); setFilterProcessing(''); setFilterPick('') }}
              className="text-xs text-primary-600 hover:text-primary-800 font-medium"
            >
              Clear all
            </button>
          )}
        </div>
        <div className="flex flex-wrap gap-4">
          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-500">Transcript status</label>
            <select
              value={filterTranscript}
              onChange={(e) => setFilterTranscript(e.target.value as VideoSummary['transcriptStatus'] | '')}
              className="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-gray-50 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-300"
            >
              <option value="">All transcript statuses</option>
              {TRANSCRIPT_STATUSES.map((s) => (
                <option key={s} value={s}>{TRANSCRIPT_LABELS[s]}</option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-500">Pick extraction</label>
            <select
              value={filterProcessing}
              onChange={(e) => setFilterProcessing(e.target.value as VideoSummary['processingStatus'] | '')}
              className="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-gray-50 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-300"
            >
              <option value="">All pick statuses</option>
              {PROCESSING_STATUSES.map((s) => (
                <option key={s} value={s}>{PROCESSING_LABELS[s]}</option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-500">Ticker</label>
            <select
              value={filterPick}
              onChange={(e) => setFilterPick(e.target.value)}
              className="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-gray-50 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-300"
            >
              <option value="">All picks</option>
              {allPicks.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>

          {(filterTranscript || filterProcessing || filterPick) && (
            <div className="flex items-end">
              <span className="text-xs text-gray-400 pb-2">
                {sorted.length} of {videos.length} videos
              </span>
            </div>
          )}
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500 uppercase tracking-wider">
              <SortTh label="#" sortKey="index" current={sortKey} dir={sortDir} onSort={toggleSort} className="w-12" />
              <th className="px-4 py-3 w-44"></th>
              <th className="px-4 py-3">Video</th>
              <SortTh label="Upload Date" sortKey="publishedAt" current={sortKey} dir={sortDir} onSort={toggleSort} className="w-28" />
              <SortTh label="Transcript" sortKey="transcriptStatus" current={sortKey} dir={sortDir} onSort={toggleSort} className="w-32" />
              <SortTh label="Picks" sortKey="processingStatus" current={sortKey} dir={sortDir} onSort={toggleSort} className="w-28" />
              <th className="px-4 py-3">Buy</th>
              <th className="px-4 py-3">Sell</th>
            </tr>
          </thead>
          <tbody>
            {sorted.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-12 text-center text-gray-400">
                  No videos match the current filters.
                </td>
              </tr>
            ) : (
              sorted.map(({ v, originalIndex }) => (
                <tr key={v.videoId} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-3 text-gray-400 font-mono">{originalIndex}</td>
                  <td className="px-4 py-3">
                    <a
                      href={`https://www.youtube.com/watch?v=${v.videoId}`}
                      target="_blank"
                      rel="noreferrer"
                    >
                      <img
                        src={`https://img.youtube.com/vi/${v.videoId}/mqdefault.jpg`}
                        alt=""
                        className="w-40 rounded object-cover aspect-video"
                      />
                    </a>
                  </td>
                  <td className="px-4 py-3">
                    <a
                      href={`https://www.youtube.com/watch?v=${v.videoId}`}
                      target="_blank"
                      rel="noreferrer"
                      className="text-gray-900 hover:text-primary-600 whitespace-nowrap overflow-hidden text-ellipsis block max-w-xl"
                    >
                      {v.title}
                    </a>
                  </td>
                  <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                    {new Date(v.publishedAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3">
                    <TranscriptBadge status={v.transcriptStatus} />
                  </td>
                  <td className="px-4 py-3">
                    <ProcessingBadge status={v.processingStatus} />
                  </td>
                  <td className="px-4 py-3 text-green-700 font-mono">
                    {v.buyPicks.length > 0 ? v.buyPicks.join(', ') : <span className="text-gray-300">—</span>}
                  </td>
                  <td className="px-4 py-3 text-red-600 font-mono">
                    {v.sellPicks.length > 0 ? v.sellPicks.join(', ') : <span className="text-gray-300">—</span>}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

const TRANSCRIPT_LABELS: Record<VideoSummary['transcriptStatus'], string> = {
  DOWNLOADED: 'Downloaded',
  NO_TRANSCRIPT: 'No transcript',
  FAILED: 'Failed',
  PENDING: 'Pending',
}

const PROCESSING_LABELS: Record<VideoSummary['processingStatus'], string> = {
  COMPLETED: 'Extracted',
  PROCESSING: 'Processing',
  FAILED: 'Failed',
  PENDING: 'Pending',
}

function SortTh({
  label, sortKey, current, dir, onSort, className,
}: {
  label: string
  sortKey: SortKey
  current: SortKey
  dir: SortDir
  onSort: (k: SortKey) => void
  className?: string
}) {
  const active = current === sortKey
  return (
    <th
      className={`px-4 py-3 cursor-pointer select-none hover:text-gray-700 ${className ?? ''}`}
      onClick={() => onSort(sortKey)}
    >
      {label}
      <span className="ml-1 text-gray-300">
        {active ? (dir === 'asc' ? '↑' : '↓') : '↕'}
      </span>
    </th>
  )
}

function TranscriptBadge({ status }: { status: VideoSummary['transcriptStatus'] }) {
  const styles: Record<VideoSummary['transcriptStatus'], string> = {
    DOWNLOADED: 'bg-green-100 text-green-700',
    NO_TRANSCRIPT: 'bg-yellow-100 text-yellow-700',
    FAILED: 'bg-red-100 text-red-700',
    PENDING: 'bg-gray-100 text-gray-500',
  }
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${styles[status]}`}>
      {TRANSCRIPT_LABELS[status]}
    </span>
  )
}

function ProcessingBadge({ status }: { status: VideoSummary['processingStatus'] }) {
  const styles: Record<VideoSummary['processingStatus'], string> = {
    COMPLETED: 'bg-green-100 text-green-700',
    PROCESSING: 'bg-blue-100 text-blue-700',
    FAILED: 'bg-red-100 text-red-700',
    PENDING: 'bg-gray-100 text-gray-500',
  }
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${styles[status]}`}>
      {PROCESSING_LABELS[status]}
    </span>
  )
}
