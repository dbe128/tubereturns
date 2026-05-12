package com.tubereturns.controller;

import com.tubereturns.dto.AiModelStatusDto;
import com.tubereturns.dto.ChannelSearchResultDto;
import com.tubereturns.dto.PipelineStepStatusDto;
import com.tubereturns.dto.YtbsdStatsDto;
import com.tubereturns.model.User;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelProcessingNotificationRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.UserRepository;
import com.tubereturns.repository.VideoRepository;
import com.tubereturns.service.AiModelService;
import com.tubereturns.service.ChannelNotificationService;
import com.tubereturns.service.PipelineSchedulerService;
import com.tubereturns.service.TranscriptDownloadService;
import com.tubereturns.service.YouTubeApiService;
import com.tubereturns.service.PipelineStatusRegistry;
import com.tubereturns.service.StockPickExtractionService;
import com.tubereturns.service.YouTubeDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Administrative operations and manual triggers")
public class AdminController {

    private final PipelineSchedulerService scheduler;
    private final PipelineStatusRegistry registry;
    private final YouTubeDiscoveryService discoveryService;
    private final YouTubeApiService youTubeApiService;
    private final StockPickExtractionService stockPickExtractionService;
    private final AiModelService aiModelService;
    private final VideoRepository videoRepository;
    private final PickRepository pickRepository;
    private final TranscriptDownloadService transcriptDownloadService;
    private final ChannelNotificationService channelNotificationService;
    private final ChannelProcessingNotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public record PendingNotificationDto(String channelName, String channelHandle, String userEmail, String requestedAt) {}

    @GetMapping("/pipeline/status")
    @Operation(summary = "Get pipeline status", description = "Returns last/next run timestamps and running state for each pipeline step")
    public List<PipelineStepStatusDto> getPipelineStatus() {
        TranscriptDownloadService.YtbsdStats stats = transcriptDownloadService.getYtbsdStats();
        YtbsdStatsDto ytbsdStatsDto = new YtbsdStatsDto(stats.totalRuns(), stats.successfulRuns(), stats.failedRuns(), stats.lastDurationMs(), stats.lastBatchSize(), stats.running(), stats.currentBatchSize(), stats.currentPhase(), stats.currentCompleted(), stats.currentTotal(), stats.currentPct());
        return List.of(
                toDto("discovery", "Video Discovery", discoveryService.getQueueSize(), null),
                toDto("transcript", "Transcript Downloads (YTBSD)", transcriptDownloadService.getQueueSize(), ytbsdStatsDto),
                toDto("extraction", "Pick Extraction", stockPickExtractionService.getQueueSize(), null, stockPickExtractionService.isWorkerRunning(), toAiModelStatusDto(aiModelService.getStatus())),
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

    @GetMapping("/channels/search")
    @Operation(summary = "Search YouTube channels", description = "Returns up to 5 YouTube channels matching the query")
    public List<ChannelSearchResultDto> searchChannels(
            @RequestParam String q,
            @RequestParam(defaultValue = "true") boolean filterByKeywords) {
        return youTubeApiService.searchChannels(q, filterByKeywords);
    }

    @DeleteMapping("/channels/{handle}")
    @Operation(summary = "Soft-delete a channel")
    public ResponseEntity<Map<String, String>> deleteChannel(@PathVariable String handle) {
        discoveryService.softDeleteChannel(handle);
        return ResponseEntity.ok(Map.of("message", "Channel deleted: " + handle));
    }

    @PostMapping("/videos/{videoId}/reextract")
    @Operation(summary = "Re-extract picks from a video", description = "Deletes existing picks, resets status to PENDING, and enqueues for extraction")
    public ResponseEntity<Map<String, String>> reextractVideo(@PathVariable String videoId) {
        return videoRepository.findByVideoId(videoId)
                .map(video -> {
                    if (video.getChannel().getHandle().startsWith("mock-")) {
                        return ResponseEntity.badRequest().<Map<String, String>>body(Map.of("message", "Operation not allowed for mock channels"));
                    }
                    pickRepository.deleteByVideoId(video.getId());
                    video.setProcessingStatus(Video.ProcessingStatus.PENDING);
                    video.setExtractionModel(null);
                    videoRepository.save(video);
                    stockPickExtractionService.enqueueForReextraction(videoId);
                    return ResponseEntity.accepted().<Map<String, String>>body(Map.of("message", "Re-extraction started for video: " + videoId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/videos/{videoId}/redownload-transcript")
    @Operation(summary = "Re-download transcript", description = "Deletes existing picks, resets statuses, and triggers transcript download")
    public ResponseEntity<Map<String, String>> redownloadTranscript(@PathVariable String videoId) {
        return videoRepository.findByVideoId(videoId)
                .map(video -> {
                    if (video.getChannel().getHandle().startsWith("mock-")) {
                        return ResponseEntity.badRequest().<Map<String, String>>body(Map.of("message", "Operation not allowed for mock channels"));
                    }
                    pickRepository.deleteByVideoId(video.getId());
                    video.setTranscriptText(null);
                    video.setTranscriptStatus(Video.TranscriptStatus.PENDING);
                    video.setProcessingStatus(Video.ProcessingStatus.PENDING);
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
                    return ResponseEntity.ok(Map.of("message", "Video " + videoId + " excluded=" + excluded));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/notifications/pending")
    @Operation(summary = "Get pending processing notifications")
    public List<PendingNotificationDto> getPendingNotifications() {
        return notificationRepository.findPending().stream()
                .map(n -> new PendingNotificationDto(
                        n.getChannel().getChannelName(),
                        n.getChannel().getHandle(),
                        n.getUser().getEmail(),
                        n.getRequestedAt().toString()))
                .toList();
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
        return ResponseEntity.ok(Map.of("message", "TubeReturns is running"));
    }

    private PipelineStepStatusDto toDto(String step, String label, Integer queueSize, YtbsdStatsDto ytbsdStats) {
        return toDto(step, label, queueSize, ytbsdStats, registry.isRunning(step), null);
    }

    private PipelineStepStatusDto toDto(String step, String label, Integer queueSize, YtbsdStatsDto ytbsdStats, boolean running) {
        return toDto(step, label, queueSize, ytbsdStats, running, null);
    }

    private PipelineStepStatusDto toDto(String step, String label, Integer queueSize, YtbsdStatsDto ytbsdStats, boolean running, AiModelStatusDto aiModelStatus) {
        return new PipelineStepStatusDto(step, label, registry.getLastStartedAt(step), registry.getLastFinishedAt(step), registry.getNextRunAt(step), running, registry.getLastRunCount(step), registry.getLimit(step), queueSize, ytbsdStats, registry.getFatalError(step), aiModelStatus);
    }

    private AiModelStatusDto toAiModelStatusDto(AiModelService.AiModelStatus s) {
        return new AiModelStatusDto(s.currentIndex(), s.currentModel(), s.model0ResetAt() != null ? s.model0ResetAt().toString() : null);
    }
}
