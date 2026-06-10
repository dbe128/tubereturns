package com.tubereturns.dto;

import java.util.List;

public record StockDetailDto(
    String tickerSymbol,
    String companyName,
    String currency,
    long totalPicks,
    long totalChannels,
    List<StockPickEntryDto> picks
) {}
