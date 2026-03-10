package com.tubereturns.dto;

import java.time.Instant;

public record ChannelResponseDto(
    Long id,
    String youtubeChannelId,
    String channelName,
    String description,
    String channelUrl,
    boolean hasThumbnail,
    Instant createdAt,
    Instant updatedAt
) {}
