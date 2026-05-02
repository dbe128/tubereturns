package com.tubereturns.controller;

import com.tubereturns.dto.ChannelResponseDto;
import com.tubereturns.dto.ChannelStatsDto;
import com.tubereturns.dto.VideoSummaryDto;
import com.tubereturns.model.Channel;
import com.tubereturns.model.Pick;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.VideoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/channels")
@Tag(name = "Channels", description = "YouTube channel management and statistics")
public class ChannelController {

    private final ChannelRepository channelRepository;
    private final PickRepository pickRepository;
    private final VideoRepository videoRepository;

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

    @GetMapping("/{handle}/stats")
    @Operation(summary = "Get channel statistics")
    public ResponseEntity<ChannelStatsDto> getChannelStats(@PathVariable String handle) {
        return channelRepository.findByHandle(handle)
                .map(c -> ResponseEntity.ok(toStatsDto(c)))
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

    @GetMapping("/top-performers")
    @Operation(summary = "Get channels ranked by pick count")
    public ResponseEntity<List<ChannelStatsDto>> getTopPerformers(
            @RequestParam(defaultValue = "10") int limit) {
        List<Channel> channels = channelRepository.findAll();
        List<ChannelStatsDto> stats = channels.stream()
                .limit(limit)
                .map(this::toStatsDto)
                .toList();
        return ResponseEntity.ok(stats);
    }

    private ChannelResponseDto toResponseDto(Channel channel) {
        return new ChannelResponseDto(
            channel.getId(),
            channel.getHandle(),
            channel.getChannelName(),
            channel.getDescription(),
            channel.getThumbnailData() != null,
            channel.getCreatedAt(),
            channel.getUpdatedAt()
        );
    }

    private VideoSummaryDto toVideoSummaryDto(Video video) {
        List<String> buyPicks = video.getPicks() == null ? List.of() : video.getPicks().stream()
                .filter(p -> p.getSignal() == Pick.Signal.BUY)
                .map(p -> p.getStock().getTickerSymbol())
                .distinct().sorted().toList();
        List<String> sellPicks = video.getPicks() == null ? List.of() : video.getPicks().stream()
                .filter(p -> p.getSignal() == Pick.Signal.SELL)
                .map(p -> p.getStock().getTickerSymbol())
                .distinct().sorted().toList();
        return new VideoSummaryDto(
            video.getVideoId(),
            video.getTitle(),
            video.getPublishedAt(),
            video.getTranscriptStatus().name(),
            video.getProcessingStatus().name(),
            video.getExtractionModel(),
            buyPicks,
            sellPicks,
            video.getTranscriptStatus() == Video.TranscriptStatus.DOWNLOADED ? video.getTranscriptText() : null,
            video.isExcluded()
        );
    }

    private ChannelStatsDto toStatsDto(Channel channel) {
        long totalVideos = videoRepository.countByChannelId(channel.getId());
        long processedVideos = videoRepository.countByChannelIdAndProcessingStatus(channel.getId(), Video.ProcessingStatus.COMPLETED);
        List<String> buyPicks = pickRepository.findDistinctTickersByChannelIdAndSignal(channel.getId(), Pick.Signal.BUY);
        List<String> sellPicks = pickRepository.findDistinctTickersByChannelIdAndSignal(channel.getId(), Pick.Signal.SELL);
        return new ChannelStatsDto(
            channel.getHandle(),
            channel.getChannelName(),
            totalVideos,
            processedVideos,
            buyPicks,
            sellPicks
        );
    }
}
