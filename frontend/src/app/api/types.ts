import { z } from 'zod';

export const SiteStatsSchema = z.object({
  totalPicks: z.number(),
  totalChannels: z.number(),
  totalStocks: z.number(),
  totalCurrencies: z.number(),
  totalLlmModels: z.number(),
  totalDeletedVideos: z.number().optional(),
  version: z.string(),
  pricesLastUpdated: z.string().nullable().optional(),
});
export type SiteStats = z.infer<typeof SiteStatsSchema>;

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
  totalVideos: z.number(),
  processedVideos: z.number(),
  score1m: z.number().nullable(),
  eligible1m: z.number(),
  unresolved1m: z.number(),
  score1y: z.number().nullable(),
  eligible1y: z.number(),
  unresolved1y: z.number(),
  score3y: z.number().nullable(),
  eligible3y: z.number(),
  unresolved3y: z.number(),
  archivarixDeletedCount: z.number().int().nullable().optional(),
});

export const VideoSummarySchema = z.object({
  videoId: z.string(),
  title: z.string(),
  publishedAt: z.string(),
  viewCount: z.number().nullable(),
  transcriptStatus: z.enum(['PENDING', 'DOWNLOADING', 'DOWNLOADED', 'NO_TRANSCRIPT', 'TOO_LONG', 'FAILED']),
  extractionStatus: z.enum(['PENDING', 'EXTRACTING', 'EXTRACTED', 'FAILED']),
  extractionModel: z.string().nullable(),
  buyPicks: z.array(z.string()),
  excluded: z.boolean(),
  exclusionReason: z.string().nullable(),
});

export const PagedVideoResponseSchema = z.object({
  content: z.array(VideoSummarySchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
export type PagedVideoResponse = z.infer<typeof PagedVideoResponseSchema>;

export const VideoTranscriptSchema = z.object({ transcriptText: z.string().nullable() });
export type VideoTranscript = z.infer<typeof VideoTranscriptSchema>;

export const PickPerformanceSchema = z.object({
  tickerSymbol: z.string(),
  companyName: z.string().nullable(),
  videoId: z.string(),
  videoTitle: z.string(),
  videoPublishedAt: z.string(),
  unknown: z.boolean(),
  approximatedPrices: z.boolean().optional(),
  corporateAction: z.string().nullable().optional(),
  return1m: z.number().nullable(),
  return1y: z.number().nullable(),
  return3y: z.number().nullable(),
  alpha1m: z.number().nullable(),
  alpha1y: z.number().nullable(),
  alpha3y: z.number().nullable(),
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
  channelId: z.string().nullable().optional(),
});

export const TickerDataSchema = z.object({
  companies: z.record(z.string(), z.string()),
  unknownTickers: z.array(z.string()),
});
export type TickerData = z.infer<typeof TickerDataSchema>;

export const StockPickEntrySchema = z.object({
  channelName: z.string(),
  channelSlug: z.string(),
  videoId: z.string(),
  videoTitle: z.string().nullable(),
  videoPublishedAt: z.string(),
  approximatedPrices: z.boolean(),
  return1m: z.number().nullable(),
  return1y: z.number().nullable(),
  return3y: z.number().nullable(),
  alpha1m: z.number().nullable(),
  alpha1y: z.number().nullable(),
  alpha3y: z.number().nullable(),
});
export type StockPickEntry = z.infer<typeof StockPickEntrySchema>;

export const StockDetailSchema = z.object({
  tickerSymbol: z.string(),
  companyName: z.string().nullable(),
  currency: z.string().nullable(),
  totalPicks: z.number(),
  totalChannels: z.number(),
  picks: z.array(StockPickEntrySchema),
});
export type StockDetail = z.infer<typeof StockDetailSchema>;

export const AiModelStatusSchema = z.object({
  currentIndex: z.number(),
  currentModel: z.string(),
  model0ResetAt: z.string().nullable(),
  lastCallDurationMs: z.number().nullable(),
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
  activeWorkers: z.number().nullable(),
});

export type Channel = z.infer<typeof ChannelSchema>;
export type VideoSummary = z.infer<typeof VideoSummarySchema>;
export type PickPerformance = z.infer<typeof PickPerformanceSchema>;
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

export const BlacklistedTickerSchema = z.object({
  id: z.number(),
  tickerSymbol: z.string(),
  reason: z.string(),
  createdAt: z.string(),
});
export type BlacklistedTicker = z.infer<typeof BlacklistedTickerSchema>;

export const ChannelSuggestionSchema = z.object({
  handle: z.string(),
  channelName: z.string(),
  channelUrl: z.string().nullable(),
  description: z.string().nullable(),
  subscriberCount: z.number().nullable(),
  suggestionCount: z.number(),
  firstSuggestedAt: z.string(),
  status: z.string(),
});
export type ChannelSuggestion = z.infer<typeof ChannelSuggestionSchema>;

export const MyChannelSuggestionSchema = z.object({
  handle: z.string(),
  channelName: z.string(),
  channelUrl: z.string().nullable(),
  description: z.string().nullable(),
  subscriberCount: z.number().nullable(),
  notifyOnComplete: z.boolean(),
  subscribedAt: z.string(),
  status: z.string(),
});
export type MyChannelSuggestion = z.infer<typeof MyChannelSuggestionSchema>;

export const FeatureFlagSchema = z.object({
  key: z.string(),
  enabled: z.boolean(),
  description: z.string().nullable(),
});
export const FeatureFlagsSchema = z.array(FeatureFlagSchema);
export type FeatureFlag = z.infer<typeof FeatureFlagSchema>;

export const TrendingPickSchema = z.object({
  tickerSymbol: z.string(),
  companyName: z.string(),
  pickCount: z.number(),
});
export const TrendingPicksSchema = z.array(TrendingPickSchema);
export type TrendingPick = z.infer<typeof TrendingPickSchema>;

export const PendingNotificationSchema = z.object({
  channelName: z.string(),
  channelHandle: z.string(),
  userEmail: z.string(),
  requestedAt: z.string(),
});

export const NotificationsStatusSchema = z.object({
  nextRunAt: z.string().nullable(),
  lastRunAt: z.string().nullable(),
  items: z.array(PendingNotificationSchema),
});

export const ChannelRelevanceSchema = z.object({
  score: z.number(),
  passed: z.boolean(),
});

export type NotificationsStatus = z.infer<typeof NotificationsStatusSchema>;
export type ChannelSearchResult = z.infer<typeof ChannelSearchResultSchema>;
export type ChannelRelevance = z.infer<typeof ChannelRelevanceSchema>;
export type RegisterResponse = z.infer<typeof RegisterResponseSchema>;
export type AuthResponse = z.infer<typeof AuthResponseSchema>;
export type MessageResponse = z.infer<typeof MessageResponseSchema>;
