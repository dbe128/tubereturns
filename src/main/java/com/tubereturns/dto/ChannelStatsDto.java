package com.tubereturns.dto;

public record ChannelStatsDto(
    String youtubeChannelId,
    String channelName,
    Integer totalPicks
) {}
