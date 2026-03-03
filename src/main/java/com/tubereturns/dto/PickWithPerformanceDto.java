package com.tubereturns.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PickWithPerformanceDto(
    Long id,
    String tickerSymbol,
    String companyName,
    String signal,
    BigDecimal confidenceScore,
    Instant extractionTimestamp,
    String videoId,
    String videoTitle,
    String channelId,
    String channelName,
    PerformanceDto performance
) {
    public record PerformanceDto(
        Long id,
        BigDecimal startPrice,
        BigDecimal currentPrice,
        BigDecimal return1d,
        BigDecimal return7d,
        BigDecimal return30d,
        BigDecimal return90d,
        BigDecimal return1y,
        BigDecimal returnYtd,
        Instant lastUpdated
    ) {}
}
