package com.tubereturns.controller;

import com.tubereturns.dto.ChannelResponseDto;
import com.tubereturns.dto.ChannelStatsDto;
import com.tubereturns.model.Channel;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.service.StockPerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/channels")
@Tag(name = "Channels", description = "YouTube channel management and statistics")
public class ChannelController {

    private final ChannelRepository channelRepository;
    private final PickRepository pickRepository;
    private final StockPerformanceService performanceService;

    public ChannelController(ChannelRepository channelRepository,
                             PickRepository pickRepository,
                             StockPerformanceService performanceService) {
        this.channelRepository = channelRepository;
        this.pickRepository = pickRepository;
        this.performanceService = performanceService;
    }

    @GetMapping
    @Operation(summary = "Get all active channels")
    public ResponseEntity<List<ChannelResponseDto>> getAllChannels() {
        List<Channel> channels = channelRepository.findByIsActiveTrue();
        return ResponseEntity.ok(channels.stream().map(this::toResponseDto).toList());
    }

    @GetMapping("/{channelId}")
    @Operation(summary = "Get channel by YouTube channel ID")
    public ResponseEntity<ChannelResponseDto> getChannelById(
            @Parameter(description = "YouTube channel ID") @PathVariable String channelId) {
        Optional<Channel> channel = channelRepository.findByChannelId(channelId);
        return channel.map(c -> ResponseEntity.ok(toResponseDto(c)))
                      .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{channelId}/stats")
    @Operation(summary = "Get channel performance statistics")
    public ResponseEntity<ChannelStatsDto> getChannelStats(
            @PathVariable String channelId) {
        Optional<Channel> channelOpt = channelRepository.findByChannelId(channelId);
        if (channelOpt.isEmpty()) return ResponseEntity.notFound().build();

        Channel channel = channelOpt.get();
        return ResponseEntity.ok(toStatsDto(channel));
    }

    @GetMapping("/top-performers")
    @Operation(summary = "Get channels ranked by 30-day return")
    public ResponseEntity<List<ChannelStatsDto>> getTopPerformers(
            @RequestParam(defaultValue = "10") int limit) {

        List<Channel> channels = channelRepository.findActiveChannelsOrderBySubscriberCount();
        List<ChannelStatsDto> stats = channels.stream()
                .limit(limit)
                .map(this::toStatsDto)
                .sorted((a, b) -> Double.compare(
                        b.avgReturn30d() != null ? b.avgReturn30d() : Double.NEGATIVE_INFINITY,
                        a.avgReturn30d() != null ? a.avgReturn30d() : Double.NEGATIVE_INFINITY))
                .toList();

        return ResponseEntity.ok(stats);
    }

    private ChannelResponseDto toResponseDto(Channel channel) {
        return new ChannelResponseDto(
            channel.getId(),
            channel.getChannelId(),
            channel.getChannelName(),
            channel.getDescription(),
            channel.getSubscriberCount(),
            channel.getVideoCount(),
            channel.getIsActive(),
            channel.getChannelUrl(),
            channel.getCreatedAt(),
            channel.getUpdatedAt()
        );
    }

    private ChannelStatsDto toStatsDto(Channel channel) {
        Double avgReturn30d = performanceService.getAverageReturn30dByChannel(channel.getId());
        long totalPicks = pickRepository.countByChannelId(channel.getId());
        return new ChannelStatsDto(
            channel.getChannelId(),
            channel.getChannelName(),
            avgReturn30d,
            (int) totalPicks,
            channel.getSubscriberCount()
        );
    }
}
