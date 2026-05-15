package com.tubereturns.service;

import com.tubereturns.model.Currency;
import com.tubereturns.model.ExchangeRate;
import com.tubereturns.repository.CurrencyRepository;
import com.tubereturns.repository.ExchangeRateRepository;
import com.tubereturns.repository.PickRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Service
public class ExchangeRateService {

    private static final List<String> SEED_CURRENCIES = List.of(
            "EUR", "GBP", "AUD", "CAD", "JPY", "CHF", "HKD", "HUF",
            "CNY", "SEK", "NOK", "NZD", "SGD", "MXN", "INR"
    );

    private final CurrencyRepository currencyRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final PickRepository pickRepository;
    private final CacheManager cacheManager;
    private final StartupCoordinator startupCoordinator;

    private CompletableFuture<Void> initTask;

    @PostConstruct
    void init() {
        initTask = startupCoordinator.register();
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void seedExchangeRates() {
        try {
            if (currencyRepository.count() > 0) {
                log.info("Currencies already present in DB — skipping initial exchange rate seed");
                return;
            }
            log.info("Seeding {} popular currencies with 10yr historical exchange rates", SEED_CURRENCIES.size());
            for (String code : SEED_CURRENCIES) {
                ensureCurrencyHistoricalRates(code);
            }
        } finally {
            initTask.complete(null);
        }
    }

    public void ensureCurrencyHistoricalRates(String code) {
        if (code == null || "USD".equalsIgnoreCase(code)) {
            return;
        }
        String upperCode = code.toUpperCase();
        Currency currency = currencyRepository.findByCode(upperCode)
                .orElseGet(() -> currencyRepository.save(new Currency(upperCode)));

        if (exchangeRateRepository.countByCurrencyId(currency.getId()) > 0) {
            log.info("Historical exchange rates for {} already exist — skipping", upperCode);
            return;
        }

        int inserted = fetchAndSaveRates(currency, LocalDate.now().minusYears(10), LocalDate.now());
        if (inserted > 0) {
            List<Long> affected = pickRepository.findDistinctChannelIdsByCurrency(upperCode);
            if (!affected.isEmpty()) {
                var cache = cacheManager.getCache("channelReturns");
                if (cache != null) {
                    affected.forEach(id -> cache.evict(id));
                }
            }
        }
    }

    public int refreshRecentRates() {
        List<Currency> currencies = currencyRepository.findAll();
        if (currencies.isEmpty()) {
            return 0;
        }
        int total = 0;
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        for (Currency currency : currencies) {
            total += fetchAndSaveRates(currency, from, to);
        }
        log.info("Exchange rate refresh complete — {} new rate points inserted", total);
        if (total > 0) {
            var cache = cacheManager.getCache("channelReturns");
            if (cache != null) {
                cache.clear();
            }
        }
        return total;
    }

    public NavigableMap<LocalDate, Double> getUsdRates(String currencyCode, LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, Double> result = new TreeMap<>();
        if (currencyCode == null || "USD".equalsIgnoreCase(currencyCode)) {
            return result;
        }
        currencyRepository.findByCode(currencyCode.toUpperCase()).ifPresent(currency ->
                exchangeRateRepository
                        .findByCurrencyIdAndRateDateBetweenOrderByRateDateAsc(currency.getId(), from, to)
                        .forEach(r -> result.put(r.getRateDate(), r.getRateToUsd()))
        );
        return result;
    }

    private int fetchAndSaveRates(Currency currency, LocalDate from, LocalDate to) {
        String yahooTicker = currency.getCode() + "USD=X";
        try {
            Map<LocalDate, Double> rates = StockPriceService.fetchHistoricalClosePrices(yahooTicker, from, to);
            int inserted = 0;
            for (Map.Entry<LocalDate, Double> entry : rates.entrySet()) {
                if (!exchangeRateRepository.existsByCurrencyIdAndRateDate(currency.getId(), entry.getKey())) {
                    exchangeRateRepository.save(new ExchangeRate(currency, entry.getKey(), entry.getValue()));
                    inserted++;
                }
            }
            if (inserted > 0) {
                log.info("Fetched {} exchange rate points for {} ({})", inserted, currency.getCode(), yahooTicker);
            }
            return inserted;
        } catch (Exception e) {
            log.warn("Failed to fetch exchange rates for {} ({}): {}", currency.getCode(), yahooTicker, e.getMessage());
            return 0;
        }
    }
}
