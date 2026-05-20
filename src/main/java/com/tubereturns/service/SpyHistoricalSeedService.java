package com.tubereturns.service;

import com.tubereturns.model.Stock;
import com.tubereturns.model.StockPrice;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Service
public class SpyHistoricalSeedService {

    private static final String TICKER = "SPY";

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final StartupCoordinator startupCoordinator;

    private CompletableFuture<Void> initTask;

    public CompletableFuture<Void> getInitTask() { return initTask; }

    @PostConstruct
    void init() {
        initTask = startupCoordinator.register();
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void seedSpyHistory() {
        try {
            Stock spy = stockRepository.findByTickerSymbol(TICKER)
                    .orElseGet(() -> stockRepository.save(new Stock(TICKER, "SPDR S&P 500 ETF", "USD")));

            LocalDate from = LocalDate.now().minusYears(20);
            LocalDate to = LocalDate.now();

            Map<LocalDate, Double> prices = StockPriceService.fetchHistoricalClosePrices(TICKER, from, to);

            int inserted = 0;
            for (Map.Entry<LocalDate, Double> entry : prices.entrySet()) {
                inserted += insertIfAbsent(spy, entry.getKey(), entry.getValue());
            }

            log.info("SPY historical seed complete — {} new price points inserted", inserted);
        } catch (Exception e) {
            log.error("Failed to seed SPY historical prices: {}", e.getMessage(), e);
        } finally {
            initTask.complete(null);
        }
    }

    public int insertIfAbsent(Stock stock, LocalDate date, double closePrice) {
        stockPriceRepository.upsert(stock.getId(), date, closePrice);
        return 1;
    }
}
