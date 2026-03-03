package com.tubereturns.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PickResponseDto(
    Long id,
    String tickerSymbol,
    String companyName,
    String signal,
    BigDecimal confidenceScore,
    Instant extractionTimestamp,
    String videoId,
    String videoTitle,
    String channelName
) {}