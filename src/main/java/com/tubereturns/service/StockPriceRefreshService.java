package com.tubereturns.service;

import com.tubereturns.model.Stock;
import com.tubereturns.model.StockPrice;
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
                int inserted = 0;
                for (Map.Entry<LocalDate, Double> entry : prices.entrySet()) {
                    if (!stockPriceRepository.existsByStockIdAndPriceDate(stock.getId(), entry.getKey())) {
                        stockPriceRepository.save(new StockPrice(stock, entry.getKey(), entry.getValue()));
                        inserted++;
                    }
                }
                if (inserted > 0) {
                    log.info("Price refresh: {} new price point(s) for {}", inserted, stock.getTickerSymbol());
                }
                totalInserted += inserted;
            } catch (Exception e) {
                log.warn("Price refresh failed for {}: {}", stock.getTickerSymbol(), e.getMessage());
            }
        }

        log.info("Price refresh complete — {} new price point(s) across {} stock(s)", totalInserted, stocks.size());
        return totalInserted;
    }
}
