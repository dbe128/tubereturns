package com.tubereturns.controller;

import com.tubereturns.dto.ChannelResponseDto;
import com.tubereturns.dto.ChannelStatsDto;
import com.tubereturns.model.Channel;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    @Operation(summary = "Get all channels")
    public ResponseEntity<List<ChannelResponseDto>> getAllChannels() {
        List<Channel> channels = channelRepository.findAll();
        return ResponseEntity.ok(channels.stream().map(this::toResponseDto).toList());
    }

    @GetMapping("/{channelId}")
    @Operation(summary = "Get channel by YouTube channel ID")
    public ResponseEntity<ChannelResponseDto> getChannelById(
            @Parameter(description = "YouTube channel ID") @PathVariable String channelId) {
        Optional<Channel> channel = channelRepository.findByYoutubeChannelId(channelId);
        return channel.map(c -> ResponseEntity.ok(toResponseDto(c)))
                      .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{channelId}/stats")
    @Operation(summary = "Get channel statistics")
    public ResponseEntity<ChannelStatsDto> getChannelStats(
            @PathVariable String channelId) {
        Optional<Channel> channelOpt = channelRepository.findByYoutubeChannelId(channelId);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Channel channel = channelOpt.get();
        return ResponseEntity.ok(toStatsDto(channel));
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
            channel.getYoutubeChannelId(),
            channel.getChannelName(),
            channel.getDescription(),
            channel.getChannelUrl(),
            channel.getCreatedAt(),
            channel.getUpdatedAt()
        );
    }

    private ChannelStatsDto toStatsDto(Channel channel) {
        long totalPicks = pickRepository.countByChannelId(channel.getId());
        return new ChannelStatsDto(
            channel.getYoutubeChannelId(),
            channel.getChannelName(),
            (int) totalPicks
        );
    }
}
