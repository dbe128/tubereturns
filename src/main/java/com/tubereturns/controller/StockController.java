package com.tubereturns.controller;

import com.tubereturns.model.Stock;
import com.tubereturns.model.StockPrice;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/stocks")
@Tag(name = "Stocks", description = "Stock price data")
public class StockController {

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;

    @GetMapping("/{ticker}/prices")
    @Operation(summary = "Get price history for a ticker")
    public ResponseEntity<List<PricePoint>> getPrices(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Stock stock = stockRepository.findByTickerSymbol(ticker.toUpperCase()).orElse(null);
        if (stock == null) {
            return ResponseEntity.notFound().build();
        }

        List<PricePoint> points = stockPriceRepository
                .findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(stock.getId(), from, to)
                .stream()
                .map(p -> new PricePoint(p.getPriceDate().toString(), p.getClosePrice()))
                .toList();

        return ResponseEntity.ok(points);
    }

    public record PricePoint(String date, double close) {}
}
