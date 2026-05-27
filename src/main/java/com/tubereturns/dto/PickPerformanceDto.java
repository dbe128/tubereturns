package com.tubereturns.dto;

import java.time.Instant;

public record PickPerformanceDto(
    String tickerSymbol,
    String companyName,
    String videoId,
    String videoTitle,
    Instant videoPublishedAt,
    boolean unknown,
    boolean approximatedPrices,
    Double return1m,
    Double return1y,
    Double return3y,
    Double alpha1m,
    Double alpha1y,
    Double alpha3y
) {}
