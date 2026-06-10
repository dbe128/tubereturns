package com.tubereturns.controller;

import com.tubereturns.dto.StockDetailDto;
import com.tubereturns.dto.StockPickEntryDto;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/stocks")
@Tag(name = "Stocks", description = "Per-stock pick performance")
public class StockController {

    private final StockRepository stockRepository;
    private final PickRepository pickRepository;

    @GetMapping("/{ticker}")
    @Operation(summary = "Get a stock with every channel pick and its performance")
    public ResponseEntity<StockDetailDto> getStock(
            @Parameter(description = "Stock ticker symbol") @PathVariable String ticker) {
        return stockRepository.findByTickerSymbol(ticker.toUpperCase())
                .filter(stock -> !stock.isUnknown())
                .map(stock -> {
                    List<StockPickEntryDto> picks = pickRepository.findPickEntriesByTicker(stock.getTickerSymbol());
                    long channelCount = picks.stream()
                            .map(StockPickEntryDto::channelSlug)
                            .distinct()
                            .count();
                    return ResponseEntity.ok(new StockDetailDto(
                            stock.getTickerSymbol(),
                            stock.getCompanyName(),
                            stock.getCurrency(),
                            picks.size(),
                            channelCount,
                            picks));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
