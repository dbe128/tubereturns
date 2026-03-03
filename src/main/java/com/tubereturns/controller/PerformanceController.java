package com.tubereturns.controller;

import com.tubereturns.dto.PerformanceStatsDto;
import com.tubereturns.model.Performance;
import com.tubereturns.model.Pick;
import com.tubereturns.repository.PerformanceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/performance")
@Tag(name = "Performance", description = "Stock performance data and analytics")
public class PerformanceController {

    private final PerformanceRepository performanceRepository;

    public PerformanceController(PerformanceRepository performanceRepository) {
        this.performanceRepository = performanceRepository;
    }

    @GetMapping("/ticker/{ticker}")
    @Operation(summary = "Get performance for ticker", description = "Get all performance data for a specific ticker")
    public ResponseEntity<List<Performance>> getPerformanceByTicker(
            @Parameter(description = "Stock ticker symbol") @PathVariable String ticker) {
        List<Performance> performance = performanceRepository.findByTickerSymbolOrderByExtractionTimestampDesc(ticker.toUpperCase());
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/channel/{channelId}")
    @Operation(summary = "Get performance by channel", description = "Get performance data for all picks from a channel")
    public ResponseEntity<List<Performance>> getPerformanceByChannel(
            @Parameter(description = "Channel ID") @PathVariable Long channelId) {
        List<Performance> performance = performanceRepository.findByChannelIdOrderByExtractionTimestampDesc(channelId);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/top-performers")
    @Operation(summary = "Get top performing picks", description = "Get best performing stock picks by 30-day returns")
    public ResponseEntity<List<Performance>> getTopPerformers(
            @Parameter(description = "Number of results to return") @RequestParam(defaultValue = "20") int limit) {
        List<Performance> topPerformers = performanceRepository.findBySignalOrderByReturn30dDesc(Pick.Signal.BUY)
                .stream()
                .limit(limit)
                .toList();
        return ResponseEntity.ok(topPerformers);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get overall performance statistics", description = "Get aggregated performance statistics")
    public ResponseEntity<PerformanceStatsDto> getOverallStats() {
        // This would require custom repository methods or service layer aggregation
        PerformanceStatsDto stats = new PerformanceStatsDto(
            0L, // totalPicks
            0.0, // averageReturn30d
            0.0, // bestReturn30d
            0.0, // worstReturn30d
            0.0  // successRate
        );
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{performanceId}")
    @Operation(summary = "Get performance by ID", description = "Get specific performance record by ID")
    public ResponseEntity<Performance> getPerformanceById(
            @Parameter(description = "Performance ID") @PathVariable Long performanceId) {
        Optional<Performance> performance = performanceRepository.findById(performanceId);
        return performance.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }
}