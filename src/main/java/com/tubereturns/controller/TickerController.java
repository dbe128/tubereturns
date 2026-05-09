package com.tubereturns.controller;

import com.tubereturns.repository.StockRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/tickers")
@Tag(name = "Tickers", description = "Stock ticker information")
public class TickerController {

    private final StockRepository stockRepository;

    @GetMapping
    @Operation(summary = "Get all known ticker symbols mapped to company names")
    public Map<String, String> getAllTickers() {
        return stockRepository.findAll().stream()
                .filter(s -> s.getCompanyName() != null && !s.getCompanyName().isBlank())
                .collect(Collectors.toMap(s -> s.getTickerSymbol(), s -> s.getCompanyName()));
    }
}
