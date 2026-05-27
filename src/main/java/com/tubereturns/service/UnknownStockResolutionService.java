package com.tubereturns.service;

import com.tubereturns.model.Stock;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class UnknownStockResolutionService {

    private final StockRepository stockRepository;
    private final PickRepository pickRepository;
    private final StockResolutionTransaction transaction;

    @Value("${tubereturns.pipeline.stock-resolution.max-attempts:3}")
    private int maxAttempts;

    @Value("${tubereturns.pipeline.blacklisted-tickers}")
    private String blacklistedTickersRaw;

    private Set<String> blacklistedTickers;

    @PostConstruct
    void initBlacklist() {
        blacklistedTickers = Arrays.stream(blacklistedTickersRaw.split(","))
                .map(String::strip)
                .map(String::toUpperCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        if (!blacklistedTickers.isEmpty()) {
            log.info("Blacklisted tickers: {}", blacklistedTickers);
        }
    }

    public int resolveAll(int maxItems) {
        List<Stock> all = stockRepository.findUnknownUnreviewed().stream()
                .filter(s -> s.getFailedResolutionAttempts() < maxAttempts)
                .toList();

        log.info("Stock resolution: {} total eligible (maxAttempts={}, maxItems={})",
                all.size(), maxAttempts, maxItems < 0 ? "∞" : maxItems);

        Instant now = Instant.now();
        Instant cutoff1m = now.minus(30, ChronoUnit.DAYS);
        Instant cutoff1y = now.minus(365, ChronoUnit.DAYS);
        Instant cutoff3y = now.minus(3 * 365, ChronoUnit.DAYS);

        int resolved = 0;
        int approximated = 0;
        int failed = 0;
        int skipped = 0;
        int processed = 0;

        for (Stock stock : all) {
            if (maxItems >= 0 && processed >= maxItems) {
                break;
            }

            if (blacklistedTickers.contains(stock.getTickerSymbol())) {
                log.info("Stock id={} '{}' is blacklisted — deleting stock, prices, and picks",
                        stock.getId(), stock.getTickerSymbol());
                transaction.deleteStockAndRelatedData(stock);
                skipped++;
                continue;
            }

            boolean needsWork = pickRepository.existsPickNeedingApproximation(
                    stock.getId(), cutoff1m, cutoff1y, cutoff3y);
            if (!needsWork) {
                log.info("Stock id={} '{}' — no picks with unapproximated past windows, skipping",
                        stock.getId(), stock.getTickerSymbol());
                skipped++;
                continue;
            }
            processed++;
            log.info("Processing stock id={} '{}' (company: '{}', failedAttempts: {})",
                    stock.getId(), stock.getTickerSymbol(), stock.getCompanyName(), stock.getFailedResolutionAttempts());

            boolean tickerResolved = false;
            if (stock.getCorporateAction() != null) {
                log.info("Stock id={} '{}' — corporate action '{}' already known, skipping ticker resolution",
                        stock.getId(), stock.getTickerSymbol(), stock.getCorporateAction());
            } else {
                tickerResolved = transaction.attemptTickerResolution(stock);
            }

            if (tickerResolved) {
                resolved++;
                log.info("Stock id={} '{}' → ticker resolved", stock.getId(), stock.getTickerSymbol());
            } else if ("UNAVAILABLE".equals(stock.getCorporateAction())) {
                failed++;
                log.info("Stock id={} '{}' → corporate action is UNAVAILABLE, skipping price approximation",
                        stock.getId(), stock.getTickerSymbol());
            } else {
                log.info("Stock id={} '{}' → attempting price approximation", stock.getId(), stock.getTickerSymbol());
                boolean hadPicks = transaction.approximateMissingPrices(stock);
                if (hadPicks) {
                    approximated++;
                    log.info("Stock id={} '{}' → prices approximated", stock.getId(), stock.getTickerSymbol());
                } else {
                    failed++;
                    log.info("Stock id={} '{}' → no picks found, skipped approximation", stock.getId(), stock.getTickerSymbol());
                }
            }
        }

        log.info("Stock resolution complete: {} resolved, {} approximated, {} skipped (no work needed), {} skipped (no picks)",
                resolved, approximated, skipped, failed);
        return processed;
    }
}
