import { z } from 'zod';

export const ChannelSchema = z.object({
  id: z.number(),
  handle: z.string(),
  channelName: z.string(),
  description: z.string().nullable(),
  hasThumbnail: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
  subscriberCount: z.number().nullable(),
  discoveryComplete: z.boolean(),
});

export const ChannelStatsSchema = z.object({
  handle: z.string(),
  channelName: z.string(),
  totalVideos: z.number(),
  processedVideos: z.number(),
  buyPicks: z.array(z.string()),
  sellPicks: z.array(z.string()),
});

export const VideoSummarySchema = z.object({
  videoId: z.string(),
  title: z.string(),
  publishedAt: z.string(),
  viewCount: z.number().nullable(),
  transcriptStatus: z.enum(['PENDING', 'DOWNLOADING', 'DOWNLOADED', 'NO_TRANSCRIPT', 'FAILED']),
  processingStatus: z.enum(['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED']),
  extractionModel: z.string().nullable(),
  buyPicks: z.array(z.string()),
  sellPicks: z.array(z.string()),
  transcriptText: z.string().nullable(),
  excluded: z.boolean(),
  exclusionReason: z.string().nullable(),
});

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
});

export const PickSchema = z.object({
  id: z.number(),
  tickerSymbol: z.string(),
  companyName: z.string().nullable(),
  signal: z.enum(['BUY', 'SELL']),
  confidenceScore: z.number().nullable(),
  extractionTimestamp: z.string(),
  videoId: z.string(),
  videoTitle: z.string().nullable(),
  handle: z.string(),
  channelName: z.string().nullable(),
  performance: PerformanceSchema.nullable(),
});

export const PricePointSchema = z.object({
  date: z.string(),
  close: z.number(),
});

export const PortfolioPricePointSchema = z.object({
  date: z.string(),
  changePercent: z.number(),
  close: z.number().nullable(),
});

export const PortfolioSchema = z.object({
  channelId: z.string(),
  name: z.string(),
  startDate: z.string(),
  tickers: z.array(z.string()),
});

export const ChannelSearchResultSchema = z.object({
  handle: z.string(),
  channelName: z.string(),
  channelUrl: z.string(),
  thumbnailUrl: z.string().nullable(),
  description: z.string().nullable(),
  subscriberCount: z.number().nullable(),
  videoCount: z.number().nullable(),
  channelCreatedAt: z.string().nullable(),
  latestVideoAt: z.string().nullable(),
});

export const TickerDataSchema = z.object({
  companies: z.record(z.string(), z.string()),
  unknownTickers: z.array(z.string()),
});
export type TickerData = z.infer<typeof TickerDataSchema>;

export const AiModelStatusSchema = z.object({
  currentIndex: z.number(),
  currentModel: z.string(),
  model0ResetAt: z.string().nullable(),
});

export type AiModelStatus = z.infer<typeof AiModelStatusSchema>;

export const YtbsdStatsSchema = z.object({
  totalRuns: z.number(),
  successfulRuns: z.number(),
  failedRuns: z.number(),
  lastDurationMs: z.number().nullable(),
  lastBatchSize: z.number().nullable(),
  running: z.boolean(),
  currentBatchSize: z.number().nullable(),
  currentPhase: z.string().nullable(),
  currentCompleted: z.number(),
  currentTotal: z.number(),
  currentPct: z.number().nullable(),
});

export const PipelineStepStatusSchema = z.object({
  step: z.string(),
  label: z.string(),
  lastStartedAt: z.string().nullable(),
  lastFinishedAt: z.string().nullable(),
  nextRunAt: z.string().nullable(),
  running: z.boolean(),
  lastRunCount: z.number().nullable(),
  limit: z.number().nullable(),
  queueSize: z.number().nullable(),
  ytbsdStats: YtbsdStatsSchema.nullable(),
  fatalError: z.string().nullable(),
  aiModelStatus: AiModelStatusSchema.nullable(),
  lastRunDurationMs: z.number().nullable(),
});

export type Channel = z.infer<typeof ChannelSchema>;
export type ChannelStats = z.infer<typeof ChannelStatsSchema>;
export type VideoSummary = z.infer<typeof VideoSummarySchema>;
export type Performance = z.infer<typeof PerformanceSchema>;
export type Pick = z.infer<typeof PickSchema>;
export type PricePoint = z.infer<typeof PricePointSchema>;
export type PortfolioPricePoint = z.infer<typeof PortfolioPricePointSchema>;
export type Portfolio = z.infer<typeof PortfolioSchema>;
export type YtbsdStats = z.infer<typeof YtbsdStatsSchema>;
export const RegisterResponseSchema = z.object({
  message: z.string(),
});

export const MessageResponseSchema = z.object({
  message: z.string(),
});

export const AuthResponseSchema = z.object({
  token: z.string(),
  email: z.string(),
  firstName: z.string(),
});

export type PipelineStepStatus = z.infer<typeof PipelineStepStatusSchema>;

export const UnknownStockSchema = z.object({
  id: z.number(),
  tickerSymbol: z.string(),
  companyName: z.string().nullable(),
  currency: z.string().nullable(),
  createdAt: z.string(),
  pickCount: z.number(),
});
export type UnknownStock = z.infer<typeof UnknownStockSchema>;

export const PendingNotificationSchema = z.object({
  channelName: z.string(),
  channelHandle: z.string(),
  userEmail: z.string(),
  requestedAt: z.string(),
});
export type PendingNotification = z.infer<typeof PendingNotificationSchema>;
export type ChannelSearchResult = z.infer<typeof ChannelSearchResultSchema>;
export type RegisterResponse = z.infer<typeof RegisterResponseSchema>;
export type AuthResponse = z.infer<typeof AuthResponseSchema>;
export type MessageResponse = z.infer<typeof MessageResponseSchema>;
