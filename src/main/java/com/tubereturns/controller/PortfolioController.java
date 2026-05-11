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
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private record PositionGroup(List<LocalDate> buyDates, LocalDate sellDate) {}

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
                List<Pick> buyPicks = pickRepository.findBuyPicksByChannelId(channel.getId());
                if (buyPicks.isEmpty()) {
                    return ResponseEntity.ok(List.<PortfolioPricePointDto>of());
                }
                LocalDate startDate = adjustToTradingDay(
                    buyPicks.get(0).getVideo().getPublishedAt().atZone(ZoneOffset.UTC).toLocalDate());
                Map<String, List<LocalDate>> buyDatesByTicker = pickDatesToMap(buyPicks);
                Map<String, List<LocalDate>> sellDatesByTicker = pickDatesToMap(
                    pickRepository.findSellPicksByChannelId(channel.getId()));
                LocalDate clipFrom = from != null ? LocalDate.parse(from) : null;
                return ResponseEntity.ok(buildPortfolioPrices(buyDatesByTicker, sellDatesByTicker, startDate, clipFrom));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, List<LocalDate>> pickDatesToMap(List<Pick> picks) {
        return picks.stream().collect(Collectors.groupingBy(
            p -> p.getStock().getTickerSymbol(),
            Collectors.mapping(
                p -> adjustToTradingDay(p.getVideo().getPublishedAt().atZone(ZoneOffset.UTC).toLocalDate()),
                Collectors.toList()
            )
        ));
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

    private List<PortfolioPricePointDto> buildPortfolioPrices(
            Map<String, List<LocalDate>> buyDatesByTicker,
            Map<String, List<LocalDate>> sellDatesByTicker,
            LocalDate startDate,
            LocalDate clipFrom) {

        Map<String, NavigableMap<LocalDate, Double>> tickerPrices = new TreeMap<>();
        for (String ticker : buyDatesByTicker.keySet()) {
            stockRepository.findByTickerSymbol(ticker).ifPresent(stock -> {
                List<StockPrice> prices = stockPriceRepository
                    .findByStockIdAndPriceDateGreaterThanEqualOrderByPriceDateAsc(stock.getId(), startDate.minusDays(7));
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

        Map<String, List<PositionGroup>> groupsByTicker = new HashMap<>();
        for (Map.Entry<String, List<LocalDate>> entry : buyDatesByTicker.entrySet()) {
            groupsByTicker.put(entry.getKey(), buildPositionGroups(
                entry.getValue(),
                sellDatesByTicker.getOrDefault(entry.getKey(), List.of())));
        }

        LocalDate effectiveFrom = clipFrom != null && clipFrom.isAfter(startDate) ? clipFrom : startDate;

        NavigableMap<LocalDate, Double> allDates = new TreeMap<>();
        tickerPrices.values().forEach(m -> m.keySet().forEach(d -> allDates.put(d, 0.0)));

        return allDates.tailMap(effectiveFrom).keySet().stream()
            .map(date -> {
                List<Double> returns = tickerPrices.entrySet().stream()
                    .map(e -> computeTickerReturn(
                        date,
                        groupsByTicker.getOrDefault(e.getKey(), List.of()),
                        e.getValue()))
                    .filter(r -> r != null)
                    .toList();
                double avg = returns.isEmpty() ? 0 : returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                return new PortfolioPricePointDto(date.toString(), avg, null);
            })
            .toList();
    }

    private List<PositionGroup> buildPositionGroups(List<LocalDate> buys, List<LocalDate> sells) {
        List<PositionGroup> groups = new ArrayList<>();
        int buyIdx = 0;
        for (LocalDate sellDate : sells) {
            List<LocalDate> groupBuys = new ArrayList<>();
            while (buyIdx < buys.size() && buys.get(buyIdx).isBefore(sellDate)) {
                groupBuys.add(buys.get(buyIdx++));
            }
            if (!groupBuys.isEmpty()) {
                groups.add(new PositionGroup(List.copyOf(groupBuys), sellDate));
            }
        }
        if (buyIdx < buys.size()) {
            groups.add(new PositionGroup(List.copyOf(buys.subList(buyIdx, buys.size())), null));
        }
        return groups;
    }

    private Double computeTickerReturn(LocalDate date, List<PositionGroup> groups, NavigableMap<LocalDate, Double> prices) {
        double compounded = 1.0;
        boolean hasContribution = false;
        for (PositionGroup group : groups) {
            List<LocalDate> activeBuys = group.buyDates().stream()
                .filter(b -> !b.isAfter(date))
                .toList();
            if (activeBuys.isEmpty()) {
                break;
            }
            boolean isClosed = group.sellDate() != null && !date.isBefore(group.sellDate());
            Map.Entry<LocalDate, Double> exitEntry = prices.floorEntry(isClosed ? group.sellDate() : date);
            if (exitEntry == null) {
                continue;
            }
            double exitPrice = exitEntry.getValue();
            OptionalDouble groupReturn = activeBuys.stream()
                .mapToDouble(buyDate -> {
                    Map.Entry<LocalDate, Double> buyEntry = prices.floorEntry(buyDate);
                    return (buyEntry != null && buyEntry.getValue() != 0)
                        ? (exitPrice - buyEntry.getValue()) / buyEntry.getValue()
                        : Double.NaN;
                })
                .filter(r -> !Double.isNaN(r))
                .average();
            if (groupReturn.isPresent()) {
                hasContribution = true;
                compounded *= (1.0 + groupReturn.getAsDouble());
            }
        }
        return hasContribution ? (compounded - 1.0) * 100.0 : null;
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
