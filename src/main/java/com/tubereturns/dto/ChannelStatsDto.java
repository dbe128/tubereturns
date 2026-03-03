package com.tubereturns.dto;

public record ChannelStatsDto(
    String channelId,
    String channelName,
    Double avgReturn30d,
    Integer totalPicks,
    Long subscriberCount
) {}
