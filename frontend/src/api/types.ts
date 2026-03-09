import { z } from 'zod'

// ── Channel ───────────────────────────────────────────────────────────────────

export const ChannelSchema = z.object({
  id: z.number(),
  youtubeChannelId: z.string(),
  channelName: z.string(),
  description: z.string().nullable(),
  channelUrl: z.string().nullable(),
  thumbnailUrl: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const ChannelStatsSchema = z.object({
  youtubeChannelId: z.string(),
  channelName: z.string(),
  totalVideos: z.number(),
  processedVideos: z.number(),
  buyPicks: z.array(z.string()),
  sellPicks: z.array(z.string()),
})

export type Channel = z.infer<typeof ChannelSchema>
export type ChannelStats = z.infer<typeof ChannelStatsSchema>

// ── Video Summary ─────────────────────────────────────────────────────────────

export const VideoSummarySchema = z.object({
  videoId: z.string(),
  title: z.string(),
  publishedAt: z.string(),
  transcriptStatus: z.enum(['PENDING', 'DOWNLOADED', 'NO_TRANSCRIPT', 'FAILED']),
  processingStatus: z.enum(['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED']),
  buyPicks: z.array(z.string()),
  sellPicks: z.array(z.string()),
  transcriptText: z.string().nullable(),
})

export type VideoSummary = z.infer<typeof VideoSummarySchema>

// ── Performance ───────────────────────────────────────────────────────────────

export const PerformanceSchema = z.object({
  id: z.number(),
  startPrice: z.number().nullable(),
  currentPrice: z.number().nullable(),
  return1d: z.number().nullable(),
  return7d: z.number().nullable(),
  return30d: z.number().nullable(),
  return90d: z.number().nullable(),
  return1y: z.number().nullable(),
  returnYtd: z.number().nullable(),
  lastUpdated: z.string().nullable(),
})

export type Performance = z.infer<typeof PerformanceSchema>

// ── Pick ──────────────────────────────────────────────────────────────────────

export const PickSchema = z.object({
  id: z.number(),
  tickerSymbol: z.string(),
  companyName: z.string().nullable(),
  signal: z.enum(['BUY', 'SELL']),
  confidenceScore: z.number().nullable(),
  extractionTimestamp: z.string(),
  videoId: z.string(),
  videoTitle: z.string().nullable(),
  youtubeChannelId: z.string(),
  channelName: z.string().nullable(),
  performance: PerformanceSchema.nullable(),
})

export type Pick = z.infer<typeof PickSchema>
