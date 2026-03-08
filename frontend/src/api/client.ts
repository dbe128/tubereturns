import axios from 'axios'
import { z } from 'zod'
import { ChannelSchema, ChannelStatsSchema, PickSchema, VideoSummarySchema } from './types'
import type { Channel, ChannelStats, Pick, VideoSummary } from './types'

const http = axios.create({ baseURL: '/api' })

http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (!err.response) {
      return Promise.reject(new Error('Cannot reach the backend. Make sure the server is running on port 8080.'))
    }
    if (err.response.status >= 500) {
      return Promise.reject(new Error('The service is not available, please try again later.'))
    }
    return Promise.reject(err)
  }
)

async function validated<T>(schema: z.ZodType<T>, promise: Promise<unknown>): Promise<T> {
  const data = await promise
  return schema.parse(data)
}

async function get(path: string, params?: Record<string, unknown>) {
  const res = await http.get(path, { params })
  return res.data
}

async function post(path: string, body?: unknown) {
  const res = await http.post(path, body)
  return res.data
}

// ── Channels ──────────────────────────────────────────────────────────────────

export async function fetchChannels(): Promise<Channel[]> {
  return validated(z.array(ChannelSchema), get('/channels'))
}

export async function fetchChannel(channelId: string): Promise<Channel> {
  return validated(ChannelSchema, get(`/channels/${channelId}`))
}

export async function fetchTopChannels(): Promise<ChannelStats[]> {
  return validated(z.array(ChannelStatsSchema), get('/channels/top-performers'))
}

export async function fetchChannelStats(channelId: string): Promise<ChannelStats> {
  return validated(ChannelStatsSchema, get(`/channels/${channelId}/stats`))
}

// ── Picks ─────────────────────────────────────────────────────────────────────

export async function fetchVideosForChannel(channelId: string): Promise<VideoSummary[]> {
  return validated(z.array(VideoSummarySchema), get(`/channels/${channelId}/videos`))
}

export async function fetchPicksForChannel(channelId: string): Promise<Pick[]> {
  return validated(z.array(PickSchema), get(`/picks/channel/${channelId}`))
}

export async function fetchRecentPicks(days = 30): Promise<Pick[]> {
  return validated(z.array(PickSchema), get('/picks', { days }))
}

// ── Admin ─────────────────────────────────────────────────────────────────────

export async function triggerIngestion(): Promise<void> {
  await post('/admin/ingestion/run')
}
