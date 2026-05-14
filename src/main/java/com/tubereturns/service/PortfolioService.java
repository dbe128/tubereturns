package com.tubereturns.service;

import com.tubereturns.dto.PortfolioPricePointDto;
import com.tubereturns.model.Channel;
import com.tubereturns.model.Pick;
import com.tubereturns.model.StockPrice;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PickRepository pickRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final ExchangeRateService exchangeRateService;

    private record PositionGroup(List<LocalDate> buyDates, LocalDate sellDate) {}

    public record ChannelReturns(Double return1y, Double return3y, Double return5y) {}

    public List<PortfolioPricePointDto> buildPortfolioPricesForChannel(Channel channel, LocalDate clipFrom) {
        List<Pick> buyPicks = pickRepository.findBuyPicksByChannelId(channel.getId());
        if (buyPicks.isEmpty()) {
            return List.of();
        }
        LocalDate startDate = adjustToTradingDay(
                buyPicks.get(0).getVideo().getPublishedAt().atZone(ZoneOffset.UTC).toLocalDate());
        Map<String, List<LocalDate>> buyDatesByTicker = pickDatesToMap(buyPicks);
        Map<String, List<LocalDate>> sellDatesByTicker = pickDatesToMap(
                pickRepository.findSellPicksByChannelId(channel.getId()));
        Map<String, NavigableMap<LocalDate, Double>> tickerPrices = buildTickerPriceMap(buyDatesByTicker, startDate);
        if (tickerPrices.isEmpty()) {
            return List.of();
        }
        return computePricePoints(tickerPrices, buildGroupsByTicker(buyDatesByTicker, sellDatesByTicker), startDate, clipFrom);
    }

    public ChannelReturns computeChannelReturns(Channel channel) {
        List<Pick> buyPicks = pickRepository.findBuyPicksByChannelId(channel.getId());
        if (buyPicks.isEmpty()) {
            return new ChannelReturns(null, null, null);
        }
        LocalDate startDate = adjustToTradingDay(
                buyPicks.get(0).getVideo().getPublishedAt().atZone(ZoneOffset.UTC).toLocalDate());
        Map<String, List<LocalDate>> buyDatesByTicker = pickDatesToMap(buyPicks);
        Map<String, List<LocalDate>> sellDatesByTicker = pickDatesToMap(
                pickRepository.findSellPicksByChannelId(channel.getId()));
        Map<String, NavigableMap<LocalDate, Double>> tickerPrices = buildTickerPriceMap(buyDatesByTicker, startDate);
        if (tickerPrices.isEmpty()) {
            return new ChannelReturns(null, null, null);
        }
        Map<String, List<PositionGroup>> groupsByTicker = buildGroupsByTicker(buyDatesByTicker, sellDatesByTicker);
        return new ChannelReturns(
                lastReturn(tickerPrices, groupsByTicker, startDate, LocalDate.now().minusYears(1)),
                lastReturn(tickerPrices, groupsByTicker, startDate, LocalDate.now().minusYears(3)),
                lastReturn(tickerPrices, groupsByTicker, startDate, LocalDate.now().minusYears(5)));
    }

    public LocalDate adjustToTradingDay(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.minusDays(2);
        }
        return date;
    }

    private Double lastReturn(
            Map<String, NavigableMap<LocalDate, Double>> tickerPrices,
            Map<String, List<PositionGroup>> groupsByTicker,
            LocalDate startDate, LocalDate clipFrom) {
        List<PortfolioPricePointDto> pts = computePricePoints(tickerPrices, groupsByTicker, startDate, clipFrom);
        return pts.isEmpty() ? null : pts.get(pts.size() - 1).changePercent();
    }

    private List<PortfolioPricePointDto> computePricePoints(
            Map<String, NavigableMap<LocalDate, Double>> tickerPrices,
            Map<String, List<PositionGroup>> groupsByTicker,
            LocalDate startDate,
            LocalDate clipFrom) {
        LocalDate effectiveFrom = clipFrom != null && clipFrom.isAfter(startDate) ? clipFrom : startDate;

        NavigableMap<LocalDate, Double> allDates = new TreeMap<>();
        tickerPrices.values().forEach(m -> m.keySet().forEach(d -> allDates.put(d, 0.0)));

        List<PortfolioPricePointDto> points = allDates.tailMap(effectiveFrom).keySet().stream()
                .map(date -> {
                    List<Double> returns = tickerPrices.entrySet().stream()
                            .map(e -> computeTickerReturn(
                                    date, groupsByTicker.getOrDefault(e.getKey(), List.of()), e.getValue()))
                            .filter(r -> r != null)
                            .toList();
                    double avg = returns.isEmpty() ? 0 : returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    return new PortfolioPricePointDto(date.toString(), avg, null);
                })
                .toList();

        if (points.isEmpty() || effectiveFrom.equals(startDate)) {
            return points;
        }
        double base = points.get(0).changePercent();
        double baseFactor = 1.0 + base / 100.0;
        return points.stream()
                .map(p -> new PortfolioPricePointDto(
                        p.date(),
                        (1.0 + p.changePercent() / 100.0) / baseFactor * 100.0 - 100.0,
                        p.close()))
                .toList();
    }

    private Map<String, NavigableMap<LocalDate, Double>> buildTickerPriceMap(
            Map<String, List<LocalDate>> buyDatesByTicker, LocalDate startDate) {
        Map<String, NavigableMap<LocalDate, Double>> tickerPrices = new TreeMap<>();
        for (String ticker : buyDatesByTicker.keySet()) {
            stockRepository.findByTickerSymbol(ticker).ifPresent(stock -> {
                List<StockPrice> prices = stockPriceRepository
                        .findByStockIdAndPriceDateGreaterThanEqualOrderByPriceDateAsc(stock.getId(), startDate.minusDays(7));
                NavigableMap<LocalDate, Double> priceMap = new TreeMap<>();
                prices.forEach(p -> priceMap.put(p.getPriceDate(), p.getClosePrice()));
                if (!priceMap.isEmpty()) {
                    NavigableMap<LocalDate, Double> fxRates = exchangeRateService.getUsdRates(
                            stock.getCurrency(), startDate.minusDays(7), LocalDate.now());
                    if (!fxRates.isEmpty()) {
                        NavigableMap<LocalDate, Double> usdPrices = new TreeMap<>();
                        priceMap.forEach((date, price) -> {
                            Map.Entry<LocalDate, Double> fxEntry = fxRates.floorEntry(date);
                            double rate = fxEntry != null ? fxEntry.getValue() : 1.0;
                            usdPrices.put(date, price * rate);
                        });
                        tickerPrices.put(ticker, usdPrices);
                    } else {
                        tickerPrices.put(ticker, priceMap);
                    }
                }
            });
        }
        return tickerPrices;
    }

    private Map<String, List<PositionGroup>> buildGroupsByTicker(
            Map<String, List<LocalDate>> buyDatesByTicker,
            Map<String, List<LocalDate>> sellDatesByTicker) {
        Map<String, List<PositionGroup>> groupsByTicker = new HashMap<>();
        for (Map.Entry<String, List<LocalDate>> entry : buyDatesByTicker.entrySet()) {
            groupsByTicker.put(entry.getKey(), buildPositionGroups(
                    entry.getValue(),
                    sellDatesByTicker.getOrDefault(entry.getKey(), List.of())));
        }
        return groupsByTicker;
    }

    private Map<String, List<LocalDate>> pickDatesToMap(List<Pick> picks) {
        return picks.stream().collect(Collectors.groupingBy(
                p -> p.getStock().getTickerSymbol(),
                Collectors.mapping(
                        p -> adjustToTradingDay(p.getVideo().getPublishedAt().atZone(ZoneOffset.UTC).toLocalDate()),
                        Collectors.toList())));
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
}
