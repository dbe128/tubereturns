package com.tubereturns.controller;

import com.tubereturns.dto.PortfolioPricePointDto;
import com.tubereturns.model.StockPrice;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import com.tubereturns.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final ChannelRepository channelRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final PortfolioService portfolioService;

    @GetMapping("/{channelId}/prices")
    public ResponseEntity<List<PortfolioPricePointDto>> getPortfolioPrices(
            @PathVariable String channelId,
            @RequestParam(required = false) String from) {
        if ("SPY".equalsIgnoreCase(channelId)) {
            return ResponseEntity.ok(buildSpyPrices(from));
        }
        return channelRepository.findByHandle(channelId)
                .map(channel -> {
                    LocalDate clipFrom = from != null ? LocalDate.parse(from) : null;
                    return ResponseEntity.ok(portfolioService.buildPortfolioPricesForChannel(channel, clipFrom));
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
}
