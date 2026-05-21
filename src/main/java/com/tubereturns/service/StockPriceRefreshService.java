package com.tubereturns.service;

import com.tubereturns.model.Stock;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class StockPriceRefreshService {

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    public int refreshAllPrices() {
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            log.info("Price refresh: no stocks in database");
            return 0;
        }

        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        int totalInserted = 0;

        for (Stock stock : stocks) {
            try {
                Map<LocalDate, Double> prices = StockPriceService.fetchHistoricalClosePrices(
                        stock.getTickerSymbol(), from, to);
                if (prices.isEmpty()) {
                    if (!stock.isUnknown()) {
                        stock.setUnknown(true);
                        stockRepository.save(stock);
                        log.warn("Price refresh: marking {} as unknown — no data from Yahoo Finance", stock.getTickerSymbol());
                    }
                    continue;
                }
                if (stock.isUnknown()) {
                    stock.setUnknown(false);
                    stockRepository.save(stock);
                }
                for (Map.Entry<LocalDate, Double> entry : prices.entrySet()) {
                    stockPriceRepository.upsert(stock.getId(), entry.getKey(), entry.getValue());
                }
                log.info("Price refresh: upserted {} price point(s) for {}", prices.size(), stock.getTickerSymbol());
                totalInserted += prices.size();
            } catch (Exception e) {
                if (!stock.isUnknown()) {
                    stock.setUnknown(true);
                    stockRepository.save(stock);
                    log.warn("Price refresh: marking {} as unknown — fetch failed: {}", stock.getTickerSymbol(), e.getMessage());
                }
            }
        }

        log.info("Price refresh complete — {} new price point(s) across {} stock(s)", totalInserted, stocks.size());
        return totalInserted;
    }
}
