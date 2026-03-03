import { z } from 'zod'

// ── Channel ───────────────────────────────────────────────────────────────────

export const ChannelSchema = z.object({
  id: z.number(),
  channelId: z.string(),
  channelName: z.string(),
  description: z.string().nullable(),
  subscriberCount: z.number().nullable(),
  videoCount: z.number().nullable(),
  isActive: z.boolean(),
  channelUrl: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const ChannelStatsSchema = z.object({
  channelId: z.string(),
  channelName: z.string(),
  avgReturn30d: z.number().nullable(),
  totalPicks: z.number(),
  subscriberCount: z.number().nullable(),
})

export type Channel = z.infer<typeof ChannelSchema>
export type ChannelStats = z.infer<typeof ChannelStatsSchema>

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
  channelId: z.string(),
  channelName: z.string().nullable(),
  performance: PerformanceSchema.nullable(),
})

export type Pick = z.infer<typeof PickSchema>
