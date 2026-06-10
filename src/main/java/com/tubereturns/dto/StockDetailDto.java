package com.tubereturns.dto;

import java.time.Instant;
import java.util.List;

public record StockDetailDto(
    String tickerSymbol,
    String companyName,
    String currency,
    long totalPicks,
    long totalChannels,
    List<StockPickEntry> picks
) {
    public record StockPickEntry(
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
}
