package com.tubereturns.controller;

import com.tubereturns.dto.PortfolioDto;
import com.tubereturns.dto.PortfolioPricePointDto;
import com.tubereturns.model.Pick;
import com.tubereturns.model.StockPrice;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final ChannelRepository channelRepository;
    private final PickRepository pickRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;

    @GetMapping
    public List<PortfolioDto> getPortfolios() {
        List<PortfolioDto> portfolios = new ArrayList<>();

        channelRepository.findAll().forEach(channel -> {
            List<String> tickers = pickRepository.findDistinctTickersByChannelIdAndSignal(
                channel.getId(), Pick.Signal.BUY);
            if (!tickers.isEmpty()) {
                LocalDate startDate = pickRepository.findEarliestBuyPickPublishedAt(channel.getId())
                    .map(instant -> adjustToTradingDay(instant.atZone(ZoneOffset.UTC).toLocalDate()))
                    .orElse(LocalDate.now());
                portfolios.add(new PortfolioDto(
                    channel.getHandle(),
                    channel.getChannelName(),
                    startDate.toString(),
                    tickers));
            }
        });

        return portfolios;
    }

    @GetMapping("/{channelId}/prices")
    public ResponseEntity<List<PortfolioPricePointDto>> getPortfolioPrices(
            @PathVariable String channelId,
            @RequestParam(required = false) String from) {

        if ("SPY".equalsIgnoreCase(channelId)) {
            return ResponseEntity.ok(buildSpyPrices(from));
        }

        return channelRepository.findByHandle(channelId)
            .map(channel -> {
                List<String> tickers = pickRepository.findDistinctTickersByChannelIdAndSignal(
                    channel.getId(), Pick.Signal.BUY);
                LocalDate startDate = pickRepository.findEarliestBuyPickPublishedAt(channel.getId())
                    .map(instant -> adjustToTradingDay(instant.atZone(ZoneOffset.UTC).toLocalDate()))
                    .orElse(LocalDate.now());
                LocalDate clipFrom = from != null ? LocalDate.parse(from) : null;
                return ResponseEntity.ok(buildPortfolioPrices(tickers, startDate, clipFrom));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private List<PortfolioPricePointDto> buildSpyPrices(String fromParam) {
        return stockRepository.findByTickerSymbol("SPY").map(spy -> {
            LocalDate from = fromParam != null ? LocalDate.parse(fromParam) : LocalDate.now().minusYears(1);
            List<StockPrice> prices = stockPriceRepository
                .findByStockIdAndPriceDateGreaterThanEqualOrderByPriceDateAsc(spy.getId(), from);
            if (prices.isEmpty()) {
                return List.<PortfolioPricePointDto>of();
            }
            double base = prices.get(0).getClosePrice();
            return prices.stream()
                .map(p -> new PortfolioPricePointDto(
                    p.getPriceDate().toString(),
                    base > 0 ? ((p.getClosePrice() - base) / base) * 100 : 0,
                    p.getClosePrice()))
                .toList();
        }).orElse(List.of());
    }

    private List<PortfolioPricePointDto> buildPortfolioPrices(List<String> tickers, LocalDate startDate, LocalDate clipFrom) {
        Map<String, NavigableMap<LocalDate, Double>> tickerPrices = new TreeMap<>();
        for (String ticker : tickers) {
            stockRepository.findByTickerSymbol(ticker).ifPresent(stock -> {
                List<StockPrice> prices = stockPriceRepository
                    .findByStockIdAndPriceDateGreaterThanEqualOrderByPriceDateAsc(stock.getId(), startDate);
                NavigableMap<LocalDate, Double> priceMap = new TreeMap<>();
                prices.forEach(p -> priceMap.put(p.getPriceDate(), p.getClosePrice()));
                if (!priceMap.isEmpty()) {
                    tickerPrices.put(ticker, priceMap);
                }
            });
        }

        if (tickerPrices.isEmpty()) {
            return List.of();
        }

        LocalDate effectiveFrom = clipFrom != null && clipFrom.isAfter(startDate) ? clipFrom : startDate;

        Map<String, Double> basePrices = tickerPrices.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    Map.Entry<LocalDate, Double> floor = e.getValue().floorEntry(effectiveFrom);
                    return floor != null ? floor.getValue() : e.getValue().firstEntry().getValue();
                }));

        NavigableMap<LocalDate, Double> allDates = new TreeMap<>();
        tickerPrices.values().forEach(m -> m.keySet().forEach(d -> allDates.put(d, 0.0)));

        return allDates.tailMap(effectiveFrom).keySet().stream()
            .map(date -> {
                List<Double> returns = tickerPrices.entrySet().stream()
                    .map(e -> {
                        Double base = basePrices.get(e.getKey());
                        Map.Entry<LocalDate, Double> entry = e.getValue().floorEntry(date);
                        if (base == null || base == 0 || entry == null) {
                            return null;
                        }
                        return ((entry.getValue() - base) / base) * 100;
                    })
                    .filter(r -> r != null)
                    .toList();

                double avg = returns.isEmpty() ? 0 : returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                return new PortfolioPricePointDto(date.toString(), avg, null);
            })
            .toList();
    }

    private LocalDate adjustToTradingDay(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.minusDays(2);
        }
        return date;
    }
}
