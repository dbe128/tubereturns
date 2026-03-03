package com.tubereturns.dto;

import java.time.Instant;

public record ChannelResponseDto(
    Long id,
    String channelId,
    String channelName,
    String description,
    Long subscriberCount,
    Integer videoCount,
    Boolean isActive,
    String channelUrl,
    Instant createdAt,
    Instant updatedAt
) {}
