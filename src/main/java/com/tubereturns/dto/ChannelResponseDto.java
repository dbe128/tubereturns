package com.tubereturns.dto;

import java.time.Instant;

public record ChannelResponseDto(
    Long id,
    String handle,
    String channelName,
    String description,
    boolean hasThumbnail,
    Instant createdAt,
    Instant updatedAt,
    Long subscriberCount,
    boolean discoveryComplete,
    long totalVideos,
    long processedVideos,
    Double return1y,
    Double return3y,
    Double return5y
) {}
