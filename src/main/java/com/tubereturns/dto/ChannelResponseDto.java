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
    Double score1m, int eligible1m, int unresolved1m,
    Double score1y, int eligible1y, int unresolved1y,
    Double score3y, int eligible3y, int unresolved3y
) {}
