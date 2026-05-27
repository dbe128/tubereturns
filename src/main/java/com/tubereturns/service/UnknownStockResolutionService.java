package com.tubereturns.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tubereturns.model.Pick;
import com.tubereturns.model.Stock;
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

@Slf4j
@RequiredArgsConstructor
@Service
public class UnknownStockResolutionService {

    private static final String SYSTEM_PROMPT =
            "You are a financial data assistant. Output only raw JSON — no markdown, no explanation.";

    private final AiModelService aiModelService;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final PickRepository pickRepository;
    private final PickPerformanceService pickPerformanceService;
    private final ChannelListService channelListService;
    private final ObjectMapper objectMapper;

    @Value("${tubereturns.pipeline.stock-resolution.max-attempts:3}")
    private int maxAttempts;

    public int resolveAll(int maxItems) {
        List<Stock> candidates = stockRepository.findUnknownUnreviewed().stream()
                .filter(s -> s.getResolutionAttempts() < maxAttempts)
                .limit(maxItems)
                .toList();

        log.info("Stock resolution: {} candidate(s) eligible (maxAttempts={})", candidates.size(), maxAttempts);
        int processed = 0;
        for (Stock stock : candidates) {
            boolean resolved = attemptTickerResolution(stock);
            if (!resolved) {
                approximateMissingPrices(stock);
            }
            processed++;
        }
        return processed;
    }

    @Transactional
    public boolean attemptTickerResolution(Stock stock) {
        stock.setResolutionAttempts(stock.getResolutionAttempts() + 1);
        stockRepository.save(stock);

        String userPrompt = """
                The ticker '%s' for company '%s' returned no price data on Yahoo Finance. \
                Either identify the correct current Yahoo Finance ticker and ISO currency, \
                OR identify the corporate action that explains why the stock is no longer tradeable. \
                Respond with exactly one of:
                {"type":"resolved","ticker":"XXXX","currency":"USD"}
                {"type":"corporate_action","action":"ACQUIRED|DELISTED|MERGED|BANKRUPT|NAME_CHANGED|EXCHANGE_CHANGE|PRIVATIZED|UNAVAILABLE","note":"brief reason"}
                """.formatted(stock.getTickerSymbol(), stock.getCompanyName());

        String response;
        try {
            response = aiModelService.callRaw(SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            log.warn("AI call failed for unknown stock id={} '{}': {}", stock.getId(), stock.getTickerSymbol(), e.getMessage());
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
                    return false;
                }

                Map<LocalDate, Double> prices = StockPriceService.fetchHistoricalClosePrices(
                        suggestedTicker, LocalDate.now().minusYears(10), LocalDate.now());
                if (prices.isEmpty()) {
                    log.warn("AI suggested ticker '{}' for stock id={} but Yahoo Finance returned no prices",
                            suggestedTicker, stock.getId());
                    return false;
                }

                Stock existing = stockRepository.findByTickerSymbol(suggestedTicker)
                        .filter(s -> !s.getId().equals(stock.getId()))
                        .orElse(null);

                if (existing != null) {
                    pickRepository.relinkPicks(stock, existing);
                    prices.forEach((date, price) -> stockPriceRepository.upsert(existing.getId(), date, price));
                    stockPriceRepository.deleteAllByStockId(stock.getId());
                    stockRepository.delete(stock);
                    channelListService.evictAllChannels();
                    log.info("AI resolved stock '{}' → merged into existing '{}' (stock id={})",
                            stock.getTickerSymbol(), suggestedTicker, existing.getId());
                } else {
                    if (!suggestedCurrency.isEmpty()) {
                        stock.setCurrency(suggestedCurrency);
                    }
                    stock.setTickerSymbol(suggestedTicker);
                    stock.setUnknown(false);
                    stockRepository.save(stock);
                    prices.forEach((date, price) -> stockPriceRepository.upsert(stock.getId(), date, price));
                    channelListService.evictAllChannels();
                    log.info("AI resolved stock '{}' → '{}' with {} price points",
                            stock.getTickerSymbol(), suggestedTicker, prices.size());
                }
                return true;

            } else if ("corporate_action".equals(type)) {
                String action = node.path("action").asText("");
                String note = node.path("note").asText("");
                stock.setCorporateAction(action);
                stockRepository.save(stock);
                log.info("AI identified corporate action for stock id={} '{}': {} — {}",
                        stock.getId(), stock.getTickerSymbol(), action, note);
                return false;

            } else {
                log.warn("Unexpected AI response type '{}' for stock id={}", type, stock.getId());
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to parse AI response for stock id={} '{}': {}", stock.getId(), stock.getTickerSymbol(), e.getMessage());
            return false;
        }
    }

    @Transactional
    public void approximateMissingPrices(Stock stock) {
        List<Pick> picks = pickRepository.findByStockId(stock.getId());
        if (picks.isEmpty()) {
            return;
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

        LocalDate earliest = neededDates.stream().min(LocalDate::compareTo).orElse(today.minusYears(3));
        List<LocalDate> existingDates = stockPriceRepository
                .findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(stock.getId(), earliest, today)
                .stream().map(p -> p.getPriceDate()).toList();
        Set<LocalDate> missing = new LinkedHashSet<>(neededDates);
        missing.removeAll(existingDates);

        if (missing.isEmpty()) {
            log.info("No missing price dates for unknown stock id={} '{}' — triggering return computation", stock.getId(), stock.getTickerSymbol());
            computeReturnsForPicks(picks, stock);
            return;
        }

        String corporateActionNote = stock.getCorporateAction() != null
                ? " (corporate action: " + stock.getCorporateAction() + ")" : "";
        String currency = stock.getCurrency() != null ? stock.getCurrency() : "USD";
        List<String> dateList = missing.stream().map(LocalDate::toString).toList();

        String userPrompt = """
                Provide approximate historical closing prices (in %s) for '%s' (original ticker: %s%s) \
                on these dates: %s. \
                For dates after a corporate event, use the value shareholders received \
                (acquisition price, post-merger equivalent, or 0 if bankrupt). \
                Respond with JSON only: [{"date":"YYYY-MM-DD","price":123.45}]
                """.formatted(currency, stock.getCompanyName(), stock.getTickerSymbol(),
                corporateActionNote, String.join(", ", dateList));

        String response;
        try {
            response = aiModelService.callRaw(SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            log.warn("AI price approximation call failed for stock id={} '{}': {}", stock.getId(), stock.getTickerSymbol(), e.getMessage());
            return;
        }

        try {
            JsonNode array = objectMapper.readTree(response);
            if (!array.isArray()) {
                log.warn("AI price approximation returned non-array for stock id={}", stock.getId());
                return;
            }
            int saved = 0;
            for (JsonNode item : array) {
                String dateStr = item.path("date").asText("");
                double price = item.path("price").asDouble(-1);
                if (dateStr.isEmpty() || price < 0) {
                    continue;
                }
                LocalDate date = LocalDate.parse(dateStr);
                stockPriceRepository.upsertApproximated(stock.getId(), date, price);
                saved++;
            }
            log.info("AI approximated {} price point(s) for stock id={} '{}'", saved, stock.getId(), stock.getTickerSymbol());
        } catch (Exception e) {
            log.warn("Failed to parse AI price approximation for stock id={} '{}': {}", stock.getId(), stock.getTickerSymbol(), e.getMessage());
            return;
        }

        computeReturnsForPicks(picks, stock);
    }

    private void computeReturnsForPicks(List<Pick> picks, Stock stock) {
        for (Pick pick : picks) {
            pick.setApproximatedPrices(true);
            pickRepository.save(pick);
            pickPerformanceService.computeAndSaveReturns(pick);
        }
        channelListService.evictAllChannels();
        log.info("Computed approximated returns for {} pick(s) on stock id={} '{}'",
                picks.size(), stock.getId(), stock.getTickerSymbol());
    }
}
