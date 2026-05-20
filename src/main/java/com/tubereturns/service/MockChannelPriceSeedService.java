package com.tubereturns.service;

import com.tubereturns.model.Stock;
import com.tubereturns.model.StockPrice;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Service
@Profile("dev")
public class MockChannelPriceSeedService {

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final MockChannelProvider mockChannelProvider;
    private final StartupCoordinator startupCoordinator;

    private CompletableFuture<Void> initTask;

    public CompletableFuture<Void> getInitTask() { return initTask; }

    @PostConstruct
    void init() {
        initTask = startupCoordinator.register();
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void seedPrices() {
        try {
            collectTickers().forEach((ticker, entry) -> seedTicker(ticker, entry.companyName(), entry.startDate()));
        } finally {
            initTask.complete(null);
        }
    }

    private Map<String, TickerEntry> collectTickers() {
        Map<String, TickerEntry> result = new LinkedHashMap<>();
        for (MockChannelProvider.MockChannelData channel : mockChannelProvider.getChannels()) {
            for (MockChannelProvider.MockVideoData video : channel.videos()) {
                LocalDate date = video.publishedAt().atZone(ZoneOffset.UTC).toLocalDate();
                for (MockChannelProvider.MockPickData pick : video.picks()) {
                    result.merge(pick.ticker(), new TickerEntry(pick.companyName(), date),
                        (existing, candidate) -> existing.startDate().isBefore(candidate.startDate()) ? existing : candidate);
                }
            }
        }
        return result;
    }

    private void seedTicker(String ticker, String companyName, LocalDate from) {
        try {
            Stock stock = stockRepository.findByTickerSymbol(ticker)
                .orElseGet(() -> stockRepository.save(new Stock(ticker, companyName, null)));

            Map<LocalDate, Double> prices = StockPriceService.fetchHistoricalClosePrices(ticker, from.minusDays(7), LocalDate.now());

            for (Map.Entry<LocalDate, Double> entry : prices.entrySet()) {
                stockPriceRepository.upsert(stock.getId(), entry.getKey(), entry.getValue());
            }
            log.info("Price seed for {} complete — {} price points upserted", ticker, prices.size());
        } catch (Exception e) {
            log.error("Failed to seed prices for {}: {}", ticker, e.getMessage(), e);
        }
    }

    private record TickerEntry(String companyName, LocalDate startDate) {}
}
