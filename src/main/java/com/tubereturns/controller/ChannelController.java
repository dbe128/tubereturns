package com.tubereturns.controller;

import com.tubereturns.dto.ChannelResponseDto;
import com.tubereturns.dto.ChannelSearchResultDto;
import com.tubereturns.dto.PickPerformanceDto;
import com.tubereturns.dto.VideoSummaryDto;
import com.tubereturns.model.Channel;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelProcessingNotificationRepository;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.UserRepository;
import com.tubereturns.repository.VideoRepository;
import com.tubereturns.service.ChannelNotificationService;
import com.tubereturns.service.ChannelRelevanceService;
import com.tubereturns.service.PickPerformanceService;
import com.tubereturns.service.PipelineSchedulerService;
import com.tubereturns.service.YouTubeApiService;
import com.tubereturns.service.YouTubeDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/channels")
@Tag(name = "Channels", description = "YouTube channel management and statistics")
public class ChannelController {

    private final ChannelRepository channelRepository;
    private final ChannelProcessingNotificationRepository notificationRepository;
    private final PickRepository pickRepository;
    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final ChannelNotificationService channelNotificationService;
    private final YouTubeDiscoveryService discoveryService;
    private final YouTubeApiService youTubeApiService;
    private final PipelineSchedulerService scheduler;
    private final PickPerformanceService pickPerformanceService;
    private final ChannelRelevanceService channelRelevanceService;

    @GetMapping
    @Operation(summary = "Get all channels")
    public ResponseEntity<List<ChannelResponseDto>> getAllChannels() {
        List<Channel> channels = channelRepository.findAll();
        return ResponseEntity.ok(channels.stream().map(this::toResponseDto).toList());
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
    @Operation(summary = "Get videos for a channel with pick summaries")
    public ResponseEntity<List<VideoSummaryDto>> getChannelVideos(@PathVariable String handle) {
        Optional<Channel> channelOpt = channelRepository.findByHandle(handle);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Video> videos = videoRepository.findByChannelIdOrderByPublishedAtDesc(channelOpt.get().getId());
        return ResponseEntity.ok(videos.stream().map(this::toVideoSummaryDto).toList());
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
        if (notifyOnComplete && authentication != null) {
            userRepository.findByEmail(authentication.getName())
                    .ifPresent(user -> channelNotificationService.scheduleNotification(channel, user));
        }
        scheduler.triggerDiscovery();
        return ResponseEntity.ok(Map.of("message", "Channel added successfully"));
    }

    private ChannelResponseDto toResponseDto(Channel channel) {
        long totalVideos = videoRepository.countByChannelId(channel.getId());
        long processedVideos = videoRepository.countProcessedByChannelId(channel.getId());
        PickPerformanceService.ChannelScoreResult score = pickPerformanceService.computeScoreForChannel(channel.getId());
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
            video.getTranscriptStatus() == Video.TranscriptStatus.DOWNLOADED ? video.getTranscriptText() : null,
            video.isExcluded(),
            video.getExclusionReason()
        );
    }

}
