package com.tubereturns.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tubereturns.model.Pick;
import com.tubereturns.model.Stock;
import com.tubereturns.model.StockPrice;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class StockResolutionTransaction {

    private static final String SYSTEM_PROMPT =
            "You are a financial data assistant. Output only raw JSON — no markdown, no explanation.";

    @Value("${tubereturns.ai.stock-resolution-model}")
    private String stockResolutionModel;

    private final AiModelService aiModelService;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final PickRepository pickRepository;
    private final PickPerformanceService pickPerformanceService;
    private final ChannelListService channelListService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void deleteStockAndRelatedData(Stock stock) {
        log.info("Deleting blacklisted stock id={} '{}' — removing all prices and picks",
                stock.getId(), stock.getTickerSymbol());
        stockPriceRepository.deleteAllByStockId(stock.getId());
        pickRepository.deleteAllByStockId(stock.getId());
        stockRepository.delete(stock);
        channelListService.evictAllChannels();
        log.info("Deleted blacklisted stock id={} '{}'", stock.getId(), stock.getTickerSymbol());
    }

    private void incrementFailedAttempts(Stock stock) {
        int next = stock.getFailedResolutionAttempts() + 1;
        stock.setFailedResolutionAttempts(next);
        stockRepository.save(stock);
        log.info("Incremented failedResolutionAttempts to {} for stock id={} '{}'",
                next, stock.getId(), stock.getTickerSymbol());
    }

    @Transactional
    public boolean attemptTickerResolution(Stock stock) {
        String oldTickerSymbol = stock.getTickerSymbol();
        log.info("Asking AI to resolve ticker for stock id={} '{}' (company: '{}', failedAttempts so far: {})",
                stock.getId(), oldTickerSymbol, stock.getCompanyName(), stock.getFailedResolutionAttempts());

        String userPrompt = """
                The ticker '%s' for company '%s' returned no price data on Yahoo Finance. \
                Either identify the correct current Yahoo Finance ticker and ISO currency, \
                OR identify the corporate action that explains why the stock is no longer tradeable. \
                Respond with exactly one of:
                {"type":"resolved","ticker":"XXXX","currency":"USD"}
                {"type":"corporate_action","action":"ACQUIRED|DELISTED|MERGED|BANKRUPT|EXCHANGE_CHANGE|PRIVATIZED|UNAVAILABLE","note":"brief reason"}
                """.formatted(oldTickerSymbol, stock.getCompanyName());

        String response;
        try {
            response = aiModelService.callRaw(SYSTEM_PROMPT, userPrompt, stockResolutionModel);
            log.info("AI ticker resolution response for stock id={} '{}': {}", stock.getId(), oldTickerSymbol, response);
        } catch (Exception e) {
            log.warn("AI call failed for stock id={} '{}': {}", stock.getId(), oldTickerSymbol, e.getMessage());
            incrementFailedAttempts(stock);
            return false;
        }

        try {
            JsonNode node = objectMapper.readTree(response);
            String type = node.path("type").asText();

            if ("resolved".equals(type)) {
                String suggestedTicker = node.path("ticker").asText("").trim().toUpperCase();
                String suggestedCurrency = node.path("currency").asText("").trim().toUpperCase();
                if (suggestedTicker.isEmpty()) {
                    log.warn("AI returned resolved type but no ticker for stock id={}", stock.getId());
                    incrementFailedAttempts(stock);
                    return false;
                }

                log.info("AI suggested ticker '{}' (currency: '{}') for stock id={} '{}' — fetching prices from Yahoo Finance",
                        suggestedTicker, suggestedCurrency.isEmpty() ? "unknown" : suggestedCurrency,
                        stock.getId(), oldTickerSymbol);

                Map<LocalDate, Double> prices;
                try {
                    prices = StockPriceService.fetchHistoricalClosePrices(
                            suggestedTicker, LocalDate.now().minusYears(20), LocalDate.now());
                } catch (Exception e) {
                    log.warn("Yahoo Finance fetch failed for suggested ticker '{}' (stock id={} '{}'): {}",
                            suggestedTicker, stock.getId(), oldTickerSymbol, e.getMessage());
                    incrementFailedAttempts(stock);
                    return false;
                }
                if (prices.isEmpty()) {
                    log.warn("Ticker '{}' suggested for stock id={} '{}' returned no prices from Yahoo Finance — skipping",
                            suggestedTicker, stock.getId(), oldTickerSymbol);
                    incrementFailedAttempts(stock);
                    return false;
                }

                log.info("Fetched {} price points for suggested ticker '{}'", prices.size(), suggestedTicker);

                Stock existing = stockRepository.findByTickerSymbol(suggestedTicker)
                        .filter(s -> !s.getId().equals(stock.getId()))
                        .orElse(null);

                if (existing != null) {
                    log.info("Ticker '{}' already exists as stock id={} — merging picks and deleting stock id={}",
                            suggestedTicker, existing.getId(), stock.getId());
                    pickRepository.relinkPicks(stock, existing);
                    prices.forEach((date, price) -> stockPriceRepository.upsert(existing.getId(), date, price));
                    stockPriceRepository.deleteAllByStockId(stock.getId());
                    stockRepository.delete(stock);
                    channelListService.evictAllChannels();
                    log.info("Merged stock '{}' (id={}) into '{}' (id={})",
                            oldTickerSymbol, stock.getId(), suggestedTicker, existing.getId());
                } else {
                    log.info("Updating stock id={} '{}' → ticker='{}' currency='{}', storing {} price points",
                            stock.getId(), oldTickerSymbol, suggestedTicker,
                            suggestedCurrency.isEmpty() ? "unchanged" : suggestedCurrency, prices.size());
                    if (!suggestedCurrency.isEmpty()) {
                        stock.setCurrency(suggestedCurrency);
                    }
                    stock.setTickerSymbol(suggestedTicker);
                    stock.setUnknown(false);
                    stockRepository.save(stock);
                    prices.forEach((date, price) -> stockPriceRepository.upsert(stock.getId(), date, price));
                    channelListService.evictAllChannels();
                    log.info("Stock id={} successfully resolved: '{}' → '{}'",
                            stock.getId(), oldTickerSymbol, suggestedTicker);
                }
                return true;

            } else if ("corporate_action".equals(type)) {
                String action = node.path("action").asText("");
                String note = node.path("note").asText("");
                stock.setCorporateAction(action);
                stockRepository.save(stock);
                log.info("Corporate action recorded for stock id={} '{}': {} — {}",
                        stock.getId(), oldTickerSymbol, action, note);
                return false;

            } else {
                log.warn("Unexpected AI response type '{}' for stock id={} '{}'", type, stock.getId(), oldTickerSymbol);
                incrementFailedAttempts(stock);
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to parse AI response for stock id={} '{}': {}", stock.getId(), oldTickerSymbol, e.getMessage());
            incrementFailedAttempts(stock);
            return false;
        }
    }

    @Transactional
    public boolean approximateMissingPrices(Stock stock) {
        List<Pick> picks = pickRepository.findByStockId(stock.getId());
        log.info("Stock id={} '{}' has {} pick(s) eligible for price approximation",
                stock.getId(), stock.getTickerSymbol(), picks.size());
        if (picks.isEmpty()) {
            return false;
        }

        LocalDate today = LocalDate.now();
        Set<LocalDate> neededDates = new LinkedHashSet<>();
        for (Pick pick : picks) {
            LocalDate pickDate = pick.getVideo().getPublishedAt().atZone(ZoneOffset.UTC).toLocalDate();
            neededDates.add(pickDate);
            if (today.isAfter(pickDate.plusMonths(1))) { neededDates.add(pickDate.plusMonths(1)); }
            if (today.isAfter(pickDate.plusYears(1)))  { neededDates.add(pickDate.plusYears(1)); }
            if (today.isAfter(pickDate.plusYears(3)))  { neededDates.add(pickDate.plusYears(3)); }
        }
        log.info("Need {} date(s) for stock id={} '{}': {}", neededDates.size(), stock.getId(), stock.getTickerSymbol(), neededDates);

        LocalDate earliest = neededDates.stream().min(LocalDate::compareTo).orElse(today.minusYears(3));
        Set<LocalDate> existingDates = stockPriceRepository
                .findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(stock.getId(), earliest, today)
                .stream().map(StockPrice::getPriceDate).collect(Collectors.toSet());
        Set<LocalDate> missing = new LinkedHashSet<>(neededDates);
        missing.removeAll(existingDates);
        log.info("Stock id={} '{}': {} date(s) already have prices, {} date(s) missing",
                stock.getId(), stock.getTickerSymbol(), existingDates.size(), missing.size());

        if (missing.isEmpty()) {
            log.info("All price dates already present for stock id={} '{}' — computing returns", stock.getId(), stock.getTickerSymbol());
            computeReturnsForPicks(picks, stock);
            return true;
        }

        String corporateActionNote = stock.getCorporateAction() != null
                ? " (corporate action: " + stock.getCorporateAction() + ")" : "";
        String currency = stock.getCurrency() != null ? stock.getCurrency() : "USD";
        List<String> dateList = missing.stream().map(LocalDate::toString).toList();

        log.info("Requesting AI price approximation for stock id={} '{}' in {}{}: {} date(s) — {}",
                stock.getId(), stock.getTickerSymbol(), currency, corporateActionNote, missing.size(), dateList);

        String userPrompt = """
                Provide approximate historical closing prices (in %s) for '%s' (original ticker: %s%s) \
                on these dates: %s. \
                For dates after a corporate event, use the value shareholders received \
                (acquisition price, post-merger equivalent, or 0 if bankrupt). \
                You MUST respond with a JSON array (even if there is only one date). \
                Do not wrap it in an object. Example: [{"date":"YYYY-MM-DD","price":123.45,"currency":"USD"},{"date":"YYYY-MM-DD","price":67.89,"currency":"USD"}]
                """.formatted(currency, stock.getCompanyName(), stock.getTickerSymbol(),
                corporateActionNote, String.join(", ", dateList));

        String response;
        try {
            response = aiModelService.callRaw(SYSTEM_PROMPT, userPrompt, stockResolutionModel);
            log.info("AI price approximation response for stock id={} '{}': {}", stock.getId(), stock.getTickerSymbol(), response);
        } catch (Exception e) {
            log.warn("AI price approximation call failed for stock id={} '{}': {}", stock.getId(), stock.getTickerSymbol(), e.getMessage());
            return true;
        }

        try {
            JsonNode parsed = objectMapper.readTree(response);
            JsonNode array;
            if (parsed.isArray()) {
                array = parsed;
            } else if (parsed.isObject() && parsed.has("date")) {
                array = objectMapper.createArrayNode().add(parsed);
                log.info("AI returned single object instead of array for stock id={} '{}' — wrapped into array", stock.getId(), stock.getTickerSymbol());
            } else {
                log.warn("AI price approximation returned unrecognized structure for stock id={} '{}': {}", stock.getId(), stock.getTickerSymbol(), response);
                return true;
            }
            int saved = 0;
            String resolvedCurrency = null;
            for (JsonNode item : array) {
                String dateStr = item.path("date").asText("");
                double price = item.path("price").asDouble(-1);
                if (dateStr.isEmpty() || price < 0) {
                    log.warn("Skipping invalid AI price entry for stock id={} '{}': date='{}' price={}",
                            stock.getId(), stock.getTickerSymbol(), dateStr, price);
                    continue;
                }
                if (resolvedCurrency == null) {
                    String itemCurrency = item.path("currency").asText("").trim().toUpperCase();
                    if (!itemCurrency.isEmpty()) {
                        resolvedCurrency = itemCurrency;
                    }
                }
                LocalDate date = LocalDate.parse(dateStr);
                stockPriceRepository.upsertApproximated(stock.getId(), date, price);
                log.info("Stored approximated price for stock id={} '{}': {} = {}",
                        stock.getId(), stock.getTickerSymbol(), date, price);
                saved++;
            }
            if (resolvedCurrency != null && !resolvedCurrency.equals(stock.getCurrency())) {
                log.info("AI returned currency '{}' for stock id={} '{}', updating from '{}'",
                        resolvedCurrency, stock.getId(), stock.getTickerSymbol(), stock.getCurrency());
                stock.setCurrency(resolvedCurrency);
                stockRepository.save(stock);
            }
            log.info("Saved {} approximated price point(s) for stock id={} '{}'", saved, stock.getId(), stock.getTickerSymbol());
        } catch (Exception e) {
            log.warn("Failed to parse AI price approximation for stock id={} '{}': {}", stock.getId(), stock.getTickerSymbol(), e.getMessage());
            return true;
        }

        computeReturnsForPicks(picks, stock);
        return true;
    }

    private void computeReturnsForPicks(List<Pick> picks, Stock stock) {
        log.info("Computing approximated returns for {} pick(s) on stock id={} '{}'",
                picks.size(), stock.getId(), stock.getTickerSymbol());
        for (Pick pick : picks) {
            pick.setApproximatedPrices(true);
            pickRepository.save(pick);
            pickPerformanceService.computeAndSaveReturns(pick);
            log.info("Computed returns for pick id={} (video: '{}', published: {})",
                    pick.getId(), pick.getVideo().getTitle(), pick.getVideo().getPublishedAt());
        }
        channelListService.evictAllChannels();
        log.info("Done computing returns for stock id={} '{}'", stock.getId(), stock.getTickerSymbol());
    }
}
