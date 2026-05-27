package com.tubereturns.service;

import com.tubereturns.dto.PickPerformanceDto;
import com.tubereturns.dto.PickScoringData;
import com.tubereturns.model.Pick;
import com.tubereturns.model.StockPrice;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PickPerformanceService {

    private final PickRepository pickRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final ExchangeRateService exchangeRateService;

    public List<PickPerformanceDto> computeForChannel(Long channelId) {
        return pickRepository.findPicksByChannelId(channelId).stream()
                .map(pick -> (pick.getStock().isUnknown() && !pick.isApproximatedPrices()) ? unknown(pick) : new PickPerformanceDto(
                        pick.getStock().getTickerSymbol(),
                        pick.getStock().getCompanyName(),
                        pick.getVideo().getVideoId(),
                        pick.getVideo().getTitle(),
                        pick.getVideo().getPublishedAt(),
                        false,
                        pick.isApproximatedPrices(),
                        pick.getReturn1m(), pick.getReturn1y(), pick.getReturn3y(),
                        pick.getAlpha1m(), pick.getAlpha1y(), pick.getAlpha3y()
                ))
                .toList();
    }

    public void refreshLockedReturns() {
        Instant cutoff1m = LocalDate.now().minusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant cutoff1y = LocalDate.now().minusYears(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant cutoff3y = LocalDate.now().minusYears(3).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Pick> candidates = pickRepository.findPicksNeedingReturnComputation(cutoff1m, cutoff1y, cutoff3y);
        for (Pick pick : candidates) {
            computeAndSaveReturns(pick);
        }
    }

    public void computeAndSaveReturns(Pick pick) {
        if (pick.getStock().isUnknown() && !pick.isApproximatedPrices()) {
            return;
        }
        LocalDate pickDate = pick.getVideo().getPublishedAt().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate today = LocalDate.now();
        if (!today.isAfter(pickDate.plusMonths(1))) {
            return;
        }

        LocalDate from = pickDate.minusDays(7);
        LocalDate to = pickDate.plusYears(3).plusDays(7);

        List<StockPrice> rawPrices = stockPriceRepository
                .findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(pick.getStock().getId(), from, to);
        if (rawPrices.isEmpty()) {
            return;
        }
        NavigableMap<LocalDate, Double> prices = new TreeMap<>();
        rawPrices.forEach(p -> prices.put(p.getPriceDate(), p.getClosePrice()));
        NavigableMap<LocalDate, Double> usdPrices = applyFx(prices, pick.getStock().getCurrency(), from);

        Double entry = floor(usdPrices, pickDate);
        if (entry == null || entry == 0) {
            return;
        }

        NavigableMap<LocalDate, Double> spyPrices = new TreeMap<>();
        stockRepository.findByTickerSymbol("SPY").ifPresent(spy ->
                stockPriceRepository.findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(spy.getId(), from, to)
                        .forEach(p -> spyPrices.put(p.getPriceDate(), p.getClosePrice())));
        Double spyEntry = floor(spyPrices, pickDate);

        boolean changed = false;

        if (today.isAfter(pickDate.plusMonths(1)) && pick.getReturn1m() == null) {
            Double exit1m = floor(usdPrices, pickDate.plusMonths(1));
            Double spyExit1m = floor(spyPrices, pickDate.plusMonths(1));
            Double ret = pctReturn(entry, exit1m);
            Double spyRet = pctReturn(spyEntry, spyExit1m);
            pick.setReturn1m(ret);
            pick.setAlpha1m(alpha(ret, spyRet));
            changed = true;
        }
        if (today.isAfter(pickDate.plusYears(1)) && pick.getReturn1y() == null) {
            Double exit1y = floor(usdPrices, pickDate.plusYears(1));
            Double spyExit1y = floor(spyPrices, pickDate.plusYears(1));
            Double ret = pctReturn(entry, exit1y);
            Double spyRet = pctReturn(spyEntry, spyExit1y);
            pick.setReturn1y(ret);
            pick.setAlpha1y(alpha(ret, spyRet));
            changed = true;
        }
        if (today.isAfter(pickDate.plusYears(3)) && pick.getReturn3y() == null) {
            Double exit3y = floor(usdPrices, pickDate.plusYears(3));
            Double spyExit3y = floor(spyPrices, pickDate.plusYears(3));
            Double ret = pctReturn(entry, exit3y);
            Double spyRet = pctReturn(spyEntry, spyExit3y);
            pick.setReturn3y(ret);
            pick.setAlpha3y(alpha(ret, spyRet));
            changed = true;
        }

        if (changed) {
            pickRepository.save(pick);
        }
    }

    private NavigableMap<LocalDate, Double> applyFx(
            NavigableMap<LocalDate, Double> prices, String currency, LocalDate from) {
        if (currency == null || "USD".equalsIgnoreCase(currency)) {
            return prices;
        }
        NavigableMap<LocalDate, Double> fxRates = exchangeRateService.getUsdRates(currency, from, LocalDate.now());
        if (fxRates.isEmpty()) {
            return prices;
        }
        NavigableMap<LocalDate, Double> usd = new TreeMap<>();
        prices.forEach((date, price) -> {
            Map.Entry<LocalDate, Double> fx = fxRates.floorEntry(date);
            usd.put(date, price * (fx != null ? fx.getValue() : 1.0));
        });
        return usd;
    }

    private Double floor(NavigableMap<LocalDate, Double> prices, LocalDate date) {
        Map.Entry<LocalDate, Double> e = prices.floorEntry(date);
        return e != null ? e.getValue() : null;
    }

    private Double pctReturn(Double entry, Double exit) {
        if (entry == null || exit == null || entry == 0) {
            return null;
        }
        return (exit - entry) / entry * 100.0;
    }

    private Double alpha(Double pick, Double spy) {
        if (pick == null || spy == null) {
            return null;
        }
        return pick - spy;
    }

    public ChannelScoreResult computeScoreForChannel(Long channelId) {
        return computeScoreFromData(pickRepository.findPickScoringDataForChannel(channelId));
    }

    public Map<Long, ChannelScoreResult> computeScoresForAllChannels() {
        Map<Long, List<PickScoringData>> byChannel = pickRepository.findAllPickDataForScoring().stream()
                .collect(Collectors.groupingBy(PickScoringData::channelId));
        Map<Long, ChannelScoreResult> results = new HashMap<>();
        byChannel.forEach((channelId, data) -> results.put(channelId, computeScoreFromData(data)));
        return results;
    }

    private ChannelScoreResult computeScoreFromData(List<PickScoringData> data) {
        LocalDate today = LocalDate.now();
        double alphaSum1m = 0, alphaSum1y = 0, alphaSum3y = 0;
        int eligible1m = 0, eligible1y = 0, eligible3y = 0;
        int unresolved1m = 0, unresolved1y = 0, unresolved3y = 0;
        for (PickScoringData d : data) {
            LocalDate pickDate = d.videoPublishedAt().atZone(ZoneOffset.UTC).toLocalDate();
            boolean isUnresolved = d.stockUnknown() && !d.approximatedPrices();
            if (today.isAfter(pickDate.plusMonths(1))) {
                if (isUnresolved) { unresolved1m++; }
                else if (d.alpha1m() != null) { alphaSum1m += d.alpha1m(); eligible1m++; }
            }
            if (today.isAfter(pickDate.plusYears(1))) {
                if (isUnresolved) { unresolved1y++; }
                else if (d.alpha1y() != null) { alphaSum1y += d.alpha1y(); eligible1y++; }
            }
            if (today.isAfter(pickDate.plusYears(3))) {
                if (isUnresolved) { unresolved3y++; }
                else if (d.alpha3y() != null) { alphaSum3y += d.alpha3y(); eligible3y++; }
            }
        }
        Double score1m = eligible1m > 0 ? alphaSum1m / eligible1m : null;
        Double score1y = eligible1y > 0 ? alphaSum1y / eligible1y : null;
        Double score3y = eligible3y > 0 ? alphaSum3y / eligible3y : null;
        return new ChannelScoreResult(score1m, eligible1m, unresolved1m, score1y, eligible1y, unresolved1y, score3y, eligible3y, unresolved3y);
    }

    public record ChannelScoreResult(
            Double score1m, int eligible1m, int unresolved1m,
            Double score1y, int eligible1y, int unresolved1y,
            Double score3y, int eligible3y, int unresolved3y
    ) {
        public static ChannelScoreResult empty() {
            return new ChannelScoreResult(null, 0, 0, null, 0, 0, null, 0, 0);
        }
    }

    private PickPerformanceDto unknown(Pick pick) {
        return new PickPerformanceDto(
                pick.getStock().getTickerSymbol(),
                pick.getStock().getCompanyName(),
                pick.getVideo().getVideoId(),
                pick.getVideo().getTitle(),
                pick.getVideo().getPublishedAt(),
                true,
                false,
                null, null, null,
                null, null, null
        );
    }
}
