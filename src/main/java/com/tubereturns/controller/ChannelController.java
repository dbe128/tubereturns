package com.tubereturns.controller;

import com.tubereturns.dto.ChannelResponseDto;
import com.tubereturns.dto.ChannelSearchResultDto;
import com.tubereturns.dto.PagedVideoResponse;
import com.tubereturns.dto.PickPerformanceDto;
import com.tubereturns.dto.VideoSummaryDto;
import com.tubereturns.dto.VideoTranscriptDto;
import com.tubereturns.model.Channel;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelProcessingNotificationRepository;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.UserRepository;
import com.tubereturns.repository.VideoRepository;
import com.tubereturns.service.ChannelListService;
import com.tubereturns.service.ChannelNotificationService;
import com.tubereturns.service.ChannelRelevanceService;
import com.tubereturns.service.PickPerformanceService;
import com.tubereturns.service.PipelineSchedulerService;
import com.tubereturns.service.YouTubeApiService;
import com.tubereturns.service.YouTubeDiscoveryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/channels")
@Tag(name = "Channels", description = "YouTube channel management and statistics")
public class ChannelController {

    private final ChannelRepository channelRepository;
    private final ChannelProcessingNotificationRepository notificationRepository;
    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final ChannelListService channelListService;
    private final ChannelNotificationService channelNotificationService;
    private final YouTubeDiscoveryService discoveryService;
    private final YouTubeApiService youTubeApiService;
    private final PipelineSchedulerService scheduler;
    private final PickPerformanceService pickPerformanceService;
    private final ChannelRelevanceService channelRelevanceService;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    private void initMetrics() {
        for (String source : List.of("ADMIN", "AUTO")) {
            Counter.builder("tubereturns.channels.added").tag("source", source).register(meterRegistry);
        }
    }

    @GetMapping
    @Operation(summary = "Get all channels")
    public ResponseEntity<List<ChannelResponseDto>> getAllChannels() {
        return ResponseEntity.ok(channelListService.getAllChannels());
    }

    @GetMapping("/{handle}")
    @Operation(summary = "Get channel by handle")
    public ResponseEntity<ChannelResponseDto> getChannelById(
            @Parameter(description = "Channel handle") @PathVariable String handle) {
        return channelRepository.findByHandle(handle)
                .map(c -> ResponseEntity.ok(toResponseDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{handle}/thumbnail")
    @Operation(summary = "Get channel thumbnail image")
    public ResponseEntity<byte[]> getChannelThumbnail(@PathVariable String handle) {
        return channelRepository.findByHandle(handle)
                .filter(c -> c.getThumbnailData() != null)
                .map(c -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(
                                c.getThumbnailContentType() != null ? c.getThumbnailContentType() : "image/jpeg"))
                        .body(c.getThumbnailData()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{handle}/videos")
    @Operation(summary = "Get paginated videos for a channel with pick summaries")
    public ResponseEntity<PagedVideoResponse> getChannelVideos(
            @PathVariable String handle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "publishedAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) String transcriptStatus,
            @RequestParam(required = false) String extractionStatus,
            @RequestParam(defaultValue = "false") boolean requirePicks,
            @RequestParam(defaultValue = "false") boolean showExcluded,
            @RequestParam(defaultValue = "true") boolean hideUnprocessed,
            @RequestParam(required = false) String tickerFilter) {
        Optional<Channel> channelOpt = channelRepository.findByHandle(handle);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String sortField = switch (sort) {
            case "viewCount", "transcriptStatus", "extractionStatus" -> sort;
            default -> "publishedAt";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page, 50, Sort.by(direction, sortField));
        Video.TranscriptStatus ts = (transcriptStatus == null || transcriptStatus.isBlank()) ? null
                : Video.TranscriptStatus.valueOf(transcriptStatus);
        Video.ExtractionStatus es = (extractionStatus == null || extractionStatus.isBlank()) ? null
                : Video.ExtractionStatus.valueOf(extractionStatus);
        String tf = (tickerFilter == null || tickerFilter.isBlank()) ? null : tickerFilter;
        Page<Video> result = videoRepository.findByChannelIdWithFilters(
                channelOpt.get().getId(), showExcluded, hideUnprocessed, ts, es, requirePicks, tf, pageRequest);
        List<VideoSummaryDto> content = result.getContent().stream().map(this::toVideoSummaryDto).toList();
        return ResponseEntity.ok(new PagedVideoResponse(
                content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/{handle}/videos/{videoId}/transcript")
    @Operation(summary = "Get transcript text for a video")
    public ResponseEntity<VideoTranscriptDto> getVideoTranscript(
            @PathVariable String handle, @PathVariable String videoId) {
        return videoRepository.findByVideoId(videoId)
                .filter(v -> v.getChannel().getHandle().equals(handle))
                .map(v -> ResponseEntity.ok(new VideoTranscriptDto(v.getTranscriptText())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{handle}/picks")
    @Operation(summary = "Get per-pick performance for a channel")
    public ResponseEntity<List<PickPerformanceDto>> getChannelPicks(@PathVariable String handle) {
        return channelRepository.findByHandle(handle)
                .map(c -> ResponseEntity.ok(pickPerformanceService.computeForChannel(c.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my-notifications")
    @Operation(summary = "Get handles of channels the current user has a pending processing notification for")
    public ResponseEntity<List<String>> getMyNotifications(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(List.of());
        }
        return userRepository.findByEmail(authentication.getName())
                .map(user -> ResponseEntity.ok(notificationRepository.findPendingHandlesByUserId(user.getId())))
                .orElse(ResponseEntity.ok(List.of()));
    }

    @PostMapping("/{handle}/notify")
    @Operation(summary = "Subscribe to processing-complete notification for a channel")
    public ResponseEntity<Void> subscribeToNotification(@PathVariable String handle, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Channel> channelOpt = channelRepository.findByHandle(handle);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        userRepository.findByEmail(authentication.getName())
                .ifPresent(user -> channelNotificationService.scheduleNotification(channelOpt.get(), user));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{handle}/notify")
    @Operation(summary = "Unsubscribe from processing-complete notification for a channel")
    public ResponseEntity<Void> unsubscribeFromNotification(@PathVariable String handle, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Channel> channelOpt = channelRepository.findByHandle(handle);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        userRepository.findByEmail(authentication.getName())
                .ifPresent(user -> channelNotificationService.cancelNotification(channelOpt.get(), user));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search YouTube channels", description = "Returns up to 5 YouTube channels matching the query")
    public List<ChannelSearchResultDto> searchChannels(
            @RequestParam String q,
            @RequestParam(defaultValue = "true") boolean filterByKeywords) {
        return youTubeApiService.searchChannels(q, filterByKeywords);
    }

    @GetMapping("/resolve")
    @Operation(summary = "Resolve a YouTube channel by handle (1 quota unit)")
    public ResponseEntity<ChannelSearchResultDto> resolveChannel(@RequestParam String handle) {
        ChannelSearchResultDto result = youTubeApiService.resolveChannelByHandle(handle);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }

    @GetMapping("/assess-relevance")
    @Operation(summary = "Assess whether a YouTube channel is a stock-picking channel")
    public ResponseEntity<Map<String, Object>> assessRelevance(
            @RequestParam String handle,
            @RequestParam String channelName) {
        ChannelRelevanceService.RelevanceResult result = channelRelevanceService.assess(channelName, handle);
        return ResponseEntity.ok(Map.of("score", result.score(), "passed", result.passed()));
    }

    @PostMapping("/{handle}/add")
    @Operation(summary = "Add new channel", description = "Add a new YouTube channel for monitoring, or undelete a previously removed one")
    public ResponseEntity<Map<String, String>> addChannel(
            @PathVariable String handle,
            @RequestParam String channelName,
            @RequestParam(required = false, defaultValue = "") String channelUrl,
            @RequestParam(required = false, defaultValue = "") String thumbnailUrl,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false) Long subscriberCount,
            @RequestParam(defaultValue = "false") boolean notifyOnComplete,
            @RequestParam(required = false, defaultValue = "ADMIN") String approvalSource,
            Authentication authentication) {
        var channel = discoveryService.createOrUpdateChannel(handle, channelName, channelUrl, thumbnailUrl, description, subscriberCount);
        channel.setApprovalSource(approvalSource);
        channelRepository.save(channel);
        meterRegistry.counter("tubereturns.channels.added", "source", approvalSource).increment();
        if (notifyOnComplete && authentication != null) {
            userRepository.findByEmail(authentication.getName())
                    .ifPresent(user -> channelNotificationService.scheduleNotification(channel, user));
        }
        scheduler.triggerDiscovery();
        channelListService.evictAllChannels();
        return ResponseEntity.ok(Map.of("message", "Channel added successfully"));
    }

    private ChannelResponseDto toResponseDto(Channel channel) {
        long totalVideos = videoRepository.countByChannelId(channel.getId());
        long processedVideos = videoRepository.countProcessedByChannelId(channel.getId());
        PickPerformanceService.ChannelScoreResult score = pickPerformanceService.computeScoreForChannel(channel.getId());
        return toResponseDto(channel, totalVideos, processedVideos, score);
    }

    private ChannelResponseDto toResponseDto(Channel channel, long totalVideos, long processedVideos, PickPerformanceService.ChannelScoreResult score) {
        return new ChannelResponseDto(
            channel.getId(),
            channel.getHandle(),
            channel.getChannelName(),
            channel.getDescription(),
            channel.getThumbnailData() != null,
            channel.getCreatedAt(),
            channel.getUpdatedAt(),
            channel.getSubscriberCount(),
            channel.isDiscoveryComplete(),
            totalVideos,
            processedVideos,
            score.score1m(), score.eligible1m(), score.unresolved1m(),
            score.score1y(), score.eligible1y(), score.unresolved1y(),
            score.score3y(), score.eligible3y(), score.unresolved3y()
        );
    }

    private VideoSummaryDto toVideoSummaryDto(Video video) {
        List<String> buyPicks = video.getPicks() == null ? List.of() : video.getPicks().stream()
                .map(p -> p.getStock().getTickerSymbol())
                .distinct().sorted().toList();
        return new VideoSummaryDto(
            video.getVideoId(),
            video.getTitle(),
            video.getPublishedAt(),
            video.getViewCount(),
            video.getTranscriptStatus().name(),
            video.getExtractionStatus().name(),
            video.getExtractionModel(),
            buyPicks,
            video.isExcluded(),
            video.getExclusionReason()
        );
    }

}
