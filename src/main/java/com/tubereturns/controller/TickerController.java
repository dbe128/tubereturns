package com.tubereturns.controller;

import com.tubereturns.repository.StockRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/tickers")
@Tag(name = "Tickers", description = "Stock ticker information")
public class TickerController {

    private final StockRepository stockRepository;

    public record TickerResponse(Map<String, String> companies, List<String> unknownTickers) {}

    @GetMapping
    @Operation(summary = "Get ticker-to-company-name map and list of tickers unknown to Yahoo Finance")
    public TickerResponse getAllTickers() {
        var all = stockRepository.findAll();
        Map<String, String> companies = all.stream()
                .filter(s -> s.getCompanyName() != null && !s.getCompanyName().isBlank())
                .collect(Collectors.toMap(s -> s.getTickerSymbol(), s -> s.getCompanyName()));
        List<String> unknownTickers = all.stream()
                .filter(s -> s.isUnknown())
                .map(s -> s.getTickerSymbol())
                .toList();
        return new TickerResponse(companies, unknownTickers);
    }
}
