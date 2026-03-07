package com.tubereturns.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test — makes a real HTTP call to Yahoo Finance.
 * Requires network access.
 */
class StockPriceServiceIT {

    @Test
    void fetchClosePrice_returnsPositivePrice() throws Exception {
        // Use a known historical date with market data (not a weekend)
        LocalDate date = LocalDate.of(2024, 1, 2);

        double price = StockPriceService.getClosePrice("AAPL", date);

        assertThat(price).isPositive();
    }
}
