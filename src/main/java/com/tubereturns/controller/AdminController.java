package com.tubereturns.controller;

import com.tubereturns.dto.AiModelStatusDto;
import com.tubereturns.dto.PipelineStepStatusDto;
import com.tubereturns.dto.UnknownStockDto;
import com.tubereturns.dto.YtbsdStatsDto;
import com.tubereturns.model.Stock;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelProcessingNotificationRepository;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import com.tubereturns.repository.UserRepository;
import com.tubereturns.repository.VideoRepository;
import com.tubereturns.service.AiModelService;
import com.tubereturns.service.ChannelListService;
import com.tubereturns.service.ChannelNotificationService;
import com.tubereturns.service.PipelineSchedulerService;
import com.tubereturns.service.PipelineStatusRegistry;
import com.tubereturns.service.StockPickExtractionService;
import com.tubereturns.service.StockPriceService;
import com.tubereturns.service.TranscriptDownloadService;
import com.tubereturns.service.YouTubeDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Administrative operations and manual triggers")
public class AdminController {

    private final PipelineSchedulerService scheduler;
    private final PipelineStatusRegistry registry;
    private final YouTubeDiscoveryService discoveryService;
    private final ChannelRepository channelRepository;
    private final ChannelListService channelListService;
    private final StockPickExtractionService stockPickExtractionService;
    private final AiModelService aiModelService;
    private final VideoRepository videoRepository;
    private final PickRepository pickRepository;
    private final TranscriptDownloadService transcriptDownloadService;
    private final ChannelNotificationService channelNotificationService;
    private final ChannelProcessingNotificationRepository notificationRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final UserRepository userRepository;
    private final BuildProperties buildProperties;

    public record PendingNotificationDto(String channelName, String channelHandle, String userEmail, String requestedAt) {}

    public record NotificationsStatusDto(String nextRunAt, String lastRunAt, List<PendingNotificationDto> items) {}

    public record TryTickerRequest(String ticker, String currency) {}

    @GetMapping("/pipeline/status")
    @Operation(summary = "Get pipeline status", description = "Returns last/next run timestamps and running state for each pipeline step")
    public List<PipelineStepStatusDto> getPipelineStatus() {
        TranscriptDownloadService.YtbsdStats stats = transcriptDownloadService.getYtbsdStats();
        YtbsdStatsDto ytbsdStatsDto = new YtbsdStatsDto(stats.totalRuns(), stats.successfulRuns(), stats.failedRuns(), stats.lastDurationMs(), stats.lastBatchSize(), stats.running(), stats.currentBatchSize(), stats.currentPhase(), stats.currentCompleted(), stats.currentTotal(), stats.currentPct());
        return List.of(
                toDto("discovery", "Video Discovery", discoveryService.getQueueSize(), null),
                toDto("transcript", "Transcript Downloads (YTBSD)", transcriptDownloadService.getQueueSize(), ytbsdStatsDto),
                toDto("extraction", "Pick Extraction", stockPickExtractionService.getQueueSize(), null, stockPickExtractionService.isWorkerRunning(), toAiModelStatusDto(aiModelService.getStatus()), stockPickExtractionService.getActiveWorkers()),
                toDto("price-refresh", "Stock Price Refresh", null, null)
        );
    }

    @PostMapping("/pipeline/{step}/trigger")
    @Operation(summary = "Trigger a pipeline step", description = "Manually triggers a single pipeline step asynchronously")
    public ResponseEntity<Map<String, String>> triggerStep(@Parameter(description = "Step name: discovery, transcript, extraction") @PathVariable String step) {
        switch (step) {
            case "discovery" -> scheduler.triggerDiscovery();
            case "transcript" -> scheduler.triggerTranscript();
            case "extraction" -> scheduler.triggerExtraction();
            case "price-refresh" -> scheduler.triggerPriceRefresh();
            default -> { return ResponseEntity.badRequest().body(Map.of("message", "Unknown step: " + step)); }
        }
        return ResponseEntity.accepted().body(Map.of("message", "Step '" + step + "' triggered"));
    }

    @DeleteMapping("/channels/{handle}")
    @Operation(summary = "Soft-delete a channel")
    public ResponseEntity<Map<String, String>> deleteChannel(@PathVariable String handle) {
        discoveryService.softDeleteChannel(handle);
        channelListService.evictAllChannels();
        return ResponseEntity.ok(Map.of("message", "Channel deleted: " + handle));
    }

    @PostMapping("/channels/{handle}/reprocess")
    @Operation(summary = "Reprocess all picks for a channel", description = "Deletes all picks for videos with downloaded transcripts, resets to PENDING, and triggers extraction")
    public ResponseEntity<Map<String, String>> reprocessChannel(@PathVariable String handle) {
        return channelRepository.findByHandle(handle)
                .map(channel -> {
                    if (handle.startsWith("mock-")) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Operation not allowed for mock channels"));
                    }
                    List<Video> videos = videoRepository.findByChannelIdOrderByPublishedAtDesc(channel.getId());
                    int count = 0;
                    for (Video video : videos) {
                        if (video.getTranscriptStatus() != Video.TranscriptStatus.DOWNLOADED) {
                            continue;
                        }
                        pickRepository.deleteByVideoId(video.getId());
                        video.setExtractionStatus(Video.ExtractionStatus.PENDING);
                        video.setExtractionModel(null);
                        if (!"Manual".equals(video.getExclusionReason())) {
                            video.setExcluded(false);
                            video.setExclusionReason(null);
                        }
                        videoRepository.save(video);
                        count++;
                    }
                    scheduler.triggerExtraction();
                    channelListService.evictAllChannels();
                    return ResponseEntity.accepted().body(Map.of("message", "Reprocessing " + count + " video(s) for channel: " + handle));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/videos/{videoId}/reextract")
    @Operation(summary = "Re-extract picks from a video", description = "Deletes existing picks, resets status to PENDING, and enqueues for extraction")
    public ResponseEntity<Map<String, String>> reextractVideo(@PathVariable String videoId) {
        return videoRepository.findByVideoId(videoId)
                .map(video -> {
                    if (video.getChannel().getHandle().startsWith("mock-")) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Operation not allowed for mock channels"));
                    }
                    pickRepository.deleteByVideoId(video.getId());
                    video.setExtractionStatus(Video.ExtractionStatus.PENDING);
                    video.setExtractionModel(null);
                    videoRepository.save(video);
                    stockPickExtractionService.enqueueForReextraction(videoId);
                    return ResponseEntity.accepted().body(Map.of("message", "Re-extraction started for video: " + videoId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/videos/{videoId}/redownload-transcript")
    @Operation(summary = "Re-download transcript", description = "Deletes existing picks, resets statuses, and triggers transcript download")
    public ResponseEntity<Map<String, String>> redownloadTranscript(@PathVariable String videoId) {
        return videoRepository.findByVideoId(videoId)
                .map(video -> {
                    if (video.getChannel().getHandle().startsWith("mock-")) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Operation not allowed for mock channels"));
                    }
                    pickRepository.deleteByVideoId(video.getId());
                    video.setTranscriptText(null);
                    video.setTranscriptStatus(Video.TranscriptStatus.PENDING);
                    video.setExtractionStatus(Video.ExtractionStatus.PENDING);
                    video.setExtractionModel(null);
                    videoRepository.save(video);
                    transcriptDownloadService.downloadTranscript(video);
                    return ResponseEntity.ok(Map.of("message", "Transcript download started for video: " + videoId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/videos/{videoId}/excluded")
    @Operation(summary = "Set video exclusion", description = "Marks a video as excluded or included from portfolio return calculations")
    public ResponseEntity<Map<String, String>> setVideoExcluded(
            @PathVariable String videoId,
            @RequestParam boolean excluded) {
        return videoRepository.findByVideoId(videoId)
                .map(video -> {
                    video.setExcluded(excluded);
                    video.setExclusionReason(excluded ? "Manual" : null);
                    videoRepository.save(video);
                    channelListService.evictAllChannels();
                    return ResponseEntity.ok(Map.of("message", "Video " + videoId + " excluded=" + excluded));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/notifications/pending")
    @Operation(summary = "Get pending processing notifications")
    public NotificationsStatusDto getPendingNotifications() {
        java.time.Instant nextRunAt = channelNotificationService.getNextRunAt();
        java.time.Instant lastRunAt = channelNotificationService.getLastRanAt();
        List<PendingNotificationDto> items = notificationRepository.findPending().stream()
                .map(n -> new PendingNotificationDto(
                        n.getChannel().getChannelName(),
                        n.getChannel().getHandle(),
                        n.getUser().getEmail(),
                        n.getRequestedAt().toString()))
                .toList();
        return new NotificationsStatusDto(
                nextRunAt != null ? nextRunAt.toString() : null,
                lastRunAt != null ? lastRunAt.toString() : null,
                items);
    }

    @PostMapping("/notifications/trigger")
    @Operation(summary = "Manually trigger the notification check and send cycle")
    public ResponseEntity<Map<String, String>> triggerNotifications() {
        channelNotificationService.checkAndSendPendingNotifications();
        return ResponseEntity.ok(Map.of("message", "Notification check completed"));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the application is running")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("message", "TubeReturns is running", "version", buildProperties.getVersion()));
    }

    @GetMapping("/user-count")
    @Operation(summary = "Get total registered user count")
    public ResponseEntity<Map<String, Long>> getUserCount() {
        return ResponseEntity.ok(Map.of("count", userRepository.count()));
    }

    @GetMapping("/stocks/unknown")
    @Operation(summary = "Get unreviewed unknown stocks with pick counts")
    public List<UnknownStockDto> getUnknownStocks() {
        return stockRepository.findUnknownUnreviewed().stream()
                .map(s -> new UnknownStockDto(
                        s.getId(),
                        s.getTickerSymbol(),
                        s.getCompanyName(),
                        s.getCurrency(),
                        s.getCreatedAt().toString(),
                        pickRepository.countByStockId(s.getId())))
                .filter(dto -> dto.pickCount() > 0)
                .sorted((a, b) -> Long.compare(b.pickCount(), a.pickCount()))
                .toList();
    }

    @PostMapping("/stocks/{id}/try-ticker")
    @Operation(summary = "Try resolving an unknown stock via Yahoo Finance")
    @Transactional
    public ResponseEntity<Map<String, String>> tryTicker(@PathVariable Long id, @RequestBody TryTickerRequest request) {
        return stockRepository.findById(id)
                .map(stock -> {
                    String oldTicker = stock.getTickerSymbol();
                    String newTicker = request.ticker().trim().toUpperCase();
                    String newCurrency = request.currency() != null && !request.currency().isBlank()
                            ? request.currency().trim().toUpperCase() : null;
                    log.info("Admin fix: attempting to resolve stock id={} '{}' → '{}' (currency: {})",
                            stock.getId(), oldTicker, newTicker, newCurrency);
                    List<Long> affectedChannelIds = new ArrayList<>(
                            pickRepository.findDistinctChannelIdsByStockId(stock.getId()));
                    try {
                        Map<LocalDate, Double> prices = StockPriceService.fetchHistoricalClosePrices(
                                newTicker, LocalDate.now().minusYears(10), LocalDate.now());
                        log.info("Yahoo Finance returned {} price point(s) for '{}'", prices.size(), newTicker);
                        if (prices.isEmpty()) {
                            log.warn("No price data found for '{}' — aborting fix for stock id={}", newTicker, stock.getId());
                            return ResponseEntity.badRequest().body(
                                    Map.of("message", "No price data found for ticker " + newTicker));
                        }

                        Stock target = stockRepository.findByTickerSymbol(newTicker)
                                .filter(existing -> !existing.getId().equals(stock.getId()))
                                .orElse(null);

                        if (target != null) {
                            log.info("Ticker '{}' already exists as stock id={} — merging stock id={} into it",
                                    newTicker, target.getId(), stock.getId());
                            affectedChannelIds.addAll(pickRepository.findDistinctChannelIdsByStockId(target.getId()));
                            long pickCount = pickRepository.countByStockId(stock.getId());
                            int relinked = pickRepository.relinkPicks(stock, target);
                            log.info("Re-linked {} pick(s) from stock id={} ('{}') to stock id={} ('{}')",
                                    relinked, stock.getId(), oldTicker, target.getId(), newTicker);
                            for (Map.Entry<LocalDate, Double> entry : prices.entrySet()) {
                                stockPriceRepository.upsert(target.getId(), entry.getKey(), entry.getValue());
                            }
                            log.info("Merged {} price point(s) into existing stock id={} ('{}')", prices.size(), target.getId(), newTicker);
                            stockPriceRepository.deleteAllByStockId(stock.getId());
                            log.info("Deleted any orphan price points for original stock id={}", stock.getId());
                            stockRepository.delete(stock);
                            log.info("Deleted original stock id={} ('{}') after merge", stock.getId(), oldTicker);
                            String msg = "Merged '" + oldTicker + "' into existing '" + newTicker + "' — re-linked "
                                    + pickCount + " pick(s), added " + prices.size() + " price point(s), original record deleted";
                            log.info("Fix complete: {}", msg);
                            channelListService.evictAllChannels();
                            return ResponseEntity.ok(Map.of("message", msg));
                        } else {
                            if (newCurrency != null) {
                                stock.setCurrency(newCurrency);
                            }
                            stock.setTickerSymbol(newTicker);
                            stock.setUnknown(false);
                            stockRepository.save(stock);
                            log.info("Updated stock id={}: '{}' → '{}', currency={}, unknown=false",
                                    stock.getId(), oldTicker, newTicker, stock.getCurrency());
                            for (Map.Entry<LocalDate, Double> entry : prices.entrySet()) {
                                stockPriceRepository.upsert(stock.getId(), entry.getKey(), entry.getValue());
                            }
                            log.info("Saved {} price point(s) for '{}' (stock id={})", prices.size(), newTicker, stock.getId());
                            String msg = "Resolved '" + oldTicker + "' as '" + newTicker + "' — saved " + prices.size() + " price point(s)";
                            log.info("Fix complete: {}", msg);
                            channelListService.evictAllChannels();
                            return ResponseEntity.ok(Map.of("message", msg));
                        }
                    } catch (Exception e) {
                        log.error("Admin fix failed for stock id={} '{}' → '{}': {}", stock.getId(), oldTicker, newTicker, e.getMessage(), e);
                        return ResponseEntity.badRequest().body(
                                Map.of("message", "Failed to fetch prices for '" + newTicker + "': " + e.getMessage()));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/stocks/{id}/accept-unknown")
    @Operation(summary = "Mark an unknown stock as reviewed")
    public ResponseEntity<Map<String, String>> acceptUnknown(@PathVariable Long id) {
        return stockRepository.findById(id)
                .map(stock -> {
                    stock.setReviewed(true);
                    stockRepository.save(stock);
                    return ResponseEntity.ok(Map.of("message", "Stock " + stock.getTickerSymbol() + " marked as reviewed"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private PipelineStepStatusDto toDto(String step, String label, Integer queueSize, YtbsdStatsDto ytbsdStats) {
        return toDto(step, label, queueSize, ytbsdStats, registry.isRunning(step), null, null);
    }

    private PipelineStepStatusDto toDto(String step, String label, Integer queueSize, YtbsdStatsDto ytbsdStats, boolean running, AiModelStatusDto aiModelStatus) {
        return toDto(step, label, queueSize, ytbsdStats, running, aiModelStatus, null);
    }

    private PipelineStepStatusDto toDto(String step, String label, Integer queueSize, YtbsdStatsDto ytbsdStats, boolean running, AiModelStatusDto aiModelStatus, Integer activeWorkers) {
        return new PipelineStepStatusDto(step, label, registry.getLastStartedAt(step), registry.getLastFinishedAt(step), registry.getNextRunAt(step), running, registry.getLastRunCount(step), registry.getLimit(step), queueSize, ytbsdStats, registry.getFatalError(step), aiModelStatus, registry.getLastRunDurationMs(step), activeWorkers);
    }

    private AiModelStatusDto toAiModelStatusDto(AiModelService.AiModelStatus s) {
        return new AiModelStatusDto(s.currentIndex(), s.currentModel(), s.model0ResetAt() != null ? s.model0ResetAt().toString() : null, s.lastCallDurationMs());
    }
}
