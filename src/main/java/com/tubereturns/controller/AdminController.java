package com.tubereturns.controller;

import com.tubereturns.dto.ChannelSearchResultDto;
import com.tubereturns.dto.PipelineStepStatusDto;
import com.tubereturns.model.Video;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.VideoRepository;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    private final VideoRepository videoRepository;
    private final PickRepository pickRepository;
    private final TranscriptDownloadService transcriptDownloadService;

    @GetMapping("/pipeline/status")
    @Operation(summary = "Get pipeline status", description = "Returns last/next run timestamps and running state for each pipeline step")
    public List<PipelineStepStatusDto> getPipelineStatus() {
        return List.of(
                toDto("discovery", "Video Discovery"),
                toDto("transcript", "Transcript Download"),
                toDto("extraction", "Pick Extraction")
        );
    }

    @PostMapping("/pipeline/{step}/trigger")
    @Operation(summary = "Trigger a pipeline step", description = "Manually triggers a single pipeline step asynchronously")
    public ResponseEntity<Map<String, String>> triggerStep(@Parameter(description = "Step name: discovery, transcript, extraction") @PathVariable String step) {
        switch (step) {
            case "discovery" -> scheduler.triggerDiscovery();
            case "transcript" -> scheduler.triggerTranscript();
            case "extraction" -> scheduler.triggerExtraction();
            default -> { return ResponseEntity.badRequest().body(Map.of("message", "Unknown step: " + step)); }
        }
        return ResponseEntity.accepted().body(Map.of("message", "Step '" + step + "' triggered"));
    }

    @GetMapping("/channels/search")
    @Operation(summary = "Search YouTube channels", description = "Returns up to 5 YouTube channels matching the query")
    public List<ChannelSearchResultDto> searchChannels(@RequestParam String q) {
        return youTubeApiService.searchChannels(q);
    }

    @DeleteMapping("/channels/{handle}")
    @Operation(summary = "Soft-delete a channel")
    public ResponseEntity<Map<String, String>> deleteChannel(@PathVariable String handle) {
        discoveryService.softDeleteChannel(handle);
        return ResponseEntity.ok(Map.of("message", "Channel deleted: " + handle));
    }

    @PostMapping("/channels/{handle}/add")
    @Operation(summary = "Add new channel", description = "Add a new YouTube channel for monitoring, or undelete a previously removed one")
    public ResponseEntity<Map<String, String>> addChannel(
            @PathVariable String handle,
            @RequestParam String channelName,
            @RequestParam(required = false, defaultValue = "") String channelUrl,
            @RequestParam(required = false, defaultValue = "") String thumbnailUrl,
            @RequestParam(required = false, defaultValue = "") String description) {
        discoveryService.createOrUpdateChannel(handle, channelName, channelUrl, thumbnailUrl, description);
        return ResponseEntity.ok(Map.of("message", "Channel added successfully"));
    }

    @PostMapping("/videos/{videoId}/reextract")
    @Operation(summary = "Re-extract picks from a video", description = "Deletes existing picks, sets status to PROCESSING, and re-runs extraction asynchronously")
    public ResponseEntity<Map<String, String>> reextractVideo(@PathVariable String videoId) {
        return videoRepository.findByVideoId(videoId)
                .map(video -> {
                    pickRepository.deleteByVideoId(video.getId());
                    video.setProcessingStatus(Video.ProcessingStatus.PROCESSING);
                    video.setExtractionModel(null);
                    videoRepository.save(video);
                    CompletableFuture.runAsync(() ->
                        videoRepository.findByVideoIdWithChannel(videoId).ifPresent(stockPickExtractionService::processVideo)
                    );
                    return ResponseEntity.accepted().<Map<String, String>>body(Map.of("message", "Re-extraction started for video: " + videoId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/videos/{videoId}/redownload-transcript")
    @Operation(summary = "Re-download transcript", description = "Deletes existing picks, resets statuses, and triggers transcript download")
    public ResponseEntity<Map<String, String>> redownloadTranscript(@PathVariable String videoId) {
        return videoRepository.findByVideoId(videoId)
                .map(video -> {
                    pickRepository.deleteByVideoId(video.getId());
                    video.setTranscriptText(null);
                    video.setTranscriptStatus(Video.TranscriptStatus.PENDING);
                    video.setProcessingStatus(Video.ProcessingStatus.PENDING);
                    video.setExtractionModel(null);
                    videoRepository.save(video);
                    CompletableFuture.runAsync(() ->
                        videoRepository.findByVideoId(videoId).ifPresent(transcriptDownloadService::downloadTranscript)
                    );
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
                    videoRepository.save(video);
                    return ResponseEntity.ok(Map.of("message", "Video " + videoId + " excluded=" + excluded));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the application is running")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("message", "TubeReturns is running"));
    }

    private PipelineStepStatusDto toDto(String step, String label) {
        return new PipelineStepStatusDto(step, label, registry.getLastStartedAt(step), registry.getLastFinishedAt(step), registry.getNextRunAt(step), registry.isRunning(step), registry.getLastRunCount(step), registry.getLimit(step));
    }
}
