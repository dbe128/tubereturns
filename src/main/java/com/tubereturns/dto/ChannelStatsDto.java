package com.tubereturns.dto;

public record ChannelStatsDto(
    String handle,
    String channelName,
    long totalVideos,
    long processedVideos,
    Double return1y,
    Double return3y,
    Double return5y
) {}
