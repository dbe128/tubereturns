package com.tubereturns.dto;

import java.time.Instant;

public record ChannelResponseDto(
    Long id,
    String youtubeChannelId,
    String channelName,
    String description,
    String channelUrl,
    Instant createdAt,
    Instant updatedAt
) {}
