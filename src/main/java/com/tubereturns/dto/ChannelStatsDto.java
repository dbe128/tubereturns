package com.tubereturns.dto;

public record ChannelStatsDto(
    String channelId,
    String channelName,
    Integer totalPicks,
    Long subscriberCount
) {}
