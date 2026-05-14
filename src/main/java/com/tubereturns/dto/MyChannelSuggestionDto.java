package com.tubereturns.dto;

import java.time.Instant;

public record MyChannelSuggestionDto(
        String handle,
        String channelName,
        String channelUrl,
        String description,
        Long subscriberCount,
        boolean notifyOnComplete,
        Instant subscribedAt,
        String status
) {}
