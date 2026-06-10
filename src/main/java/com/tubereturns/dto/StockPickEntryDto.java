package com.tubereturns.dto;

import java.time.Instant;

public record StockPickEntryDto(
    String channelName,
    String channelSlug,
    String videoId,
    String videoTitle,
    Instant videoPublishedAt,
    boolean approximatedPrices,
    Double return1m,
    Double return1y,
    Double return3y,
    Double alpha1m,
    Double alpha1y,
    Double alpha3y
) {}
