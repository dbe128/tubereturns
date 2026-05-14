package com.tubereturns.dto;

import java.time.Instant;

public record ChannelSuggestionDto(
        String handle,
        String channelName,
        String channelUrl,
        String description,
        Long subscriberCount,
        long suggestionCount,
        Instant firstSuggestedAt,
        String status
) {}
