package com.tubereturns.dto;

import java.time.Instant;

public record PickDto(
    Long id,
    String tickerSymbol,
    String companyName,
    String signal,
    String videoId,
    String videoTitle,
    String channelId,
    String channelName,
    Instant createdAt
) {}
