package com.tubereturns.controller;

import com.tubereturns.dto.StockDetailDto;
import com.tubereturns.model.Pick;
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
                    List<Pick> picks = pickRepository.findByTickerWithChannel(stock.getTickerSymbol());
                    List<StockDetailDto.StockPickEntry> entries = picks.stream()
                            .map(p -> new StockDetailDto.StockPickEntry(
                                    p.getVideo().getChannel().getChannelName(),
                                    p.getVideo().getChannel().getNameSlug(),
                                    p.getVideo().getVideoId(),
                                    p.getVideo().getTitle(),
                                    p.getVideo().getPublishedAt(),
                                    p.isApproximatedPrices(),
                                    p.getReturn1m(), p.getReturn1y(), p.getReturn3y(),
                                    p.getAlpha1m(), p.getAlpha1y(), p.getAlpha3y()))
                            .toList();
                    long channelCount = picks.stream()
                            .map(p -> p.getVideo().getChannel().getId())
                            .distinct()
                            .count();
                    return ResponseEntity.ok(new StockDetailDto(
                            stock.getTickerSymbol(),
                            stock.getCompanyName(),
                            stock.getCurrency(),
                            entries.size(),
                            channelCount,
                            entries));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
