package com.tubereturns.controller;

import com.tubereturns.dto.PickWithPerformanceDto;
import com.tubereturns.model.Performance;
import com.tubereturns.model.Pick;
import com.tubereturns.repository.PickRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/picks")
@Tag(name = "Stock Picks", description = "Stock picks extracted from YouTube videos")
public class PickController {

    private final PickRepository pickRepository;

    public PickController(PickRepository pickRepository) {
        this.pickRepository = pickRepository;
    }

    @GetMapping
    @Operation(summary = "Get recent stock picks")
    public ResponseEntity<List<PickWithPerformanceDto>> getRecentPicks(
            @Parameter(description = "Days back to search")
            @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "Filter by ticker symbol")
            @RequestParam(required = false) String ticker,
            @Parameter(description = "Filter by signal (BUY/SELL)")
            @RequestParam(required = false) String signal,
            @RequestParam(defaultValue = "50") int limit) {

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Pick> picks;

        if (ticker != null) {
            picks = pickRepository.findByTickerSymbolOrderByExtractionTimestampDesc(ticker.toUpperCase());
        } else if (signal != null) {
            try {
                Pick.Signal signalEnum = Pick.Signal.valueOf(signal.toUpperCase());
                picks = pickRepository.findBySignalOrderByExtractionTimestampDesc(signalEnum);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            picks = pickRepository.findPicksSince(since);
        }

        return ResponseEntity.ok(picks.stream().limit(limit).map(this::toDto).toList());
    }

    @GetMapping("/tickers")
    @Operation(summary = "Get all ticker symbols")
    public ResponseEntity<List<String>> getAllTickers() {
        return ResponseEntity.ok(pickRepository.findDistinctTickerSymbols());
    }

    @GetMapping("/ticker/{ticker}")
    @Operation(summary = "Get picks for a specific ticker")
    public ResponseEntity<List<PickWithPerformanceDto>> getPicksForTicker(
            @PathVariable String ticker) {
        List<Pick> picks = pickRepository.findByTickerSymbolOrderByExtractionTimestampDesc(ticker.toUpperCase());
        return ResponseEntity.ok(picks.stream().map(this::toDto).toList());
    }

    @GetMapping("/channel/{channelId}")
    @Operation(summary = "Get picks by YouTube channel ID")
    public ResponseEntity<List<PickWithPerformanceDto>> getPicksByChannel(
            @PathVariable String channelId) {
        List<Pick> picks = pickRepository.findByYouTubeChannelIdOrderByExtractionTimestampDesc(channelId);
        return ResponseEntity.ok(picks.stream().map(this::toDto).toList());
    }

    @GetMapping("/{pickId}")
    @Operation(summary = "Get pick by ID")
    public ResponseEntity<PickWithPerformanceDto> getPickById(@PathVariable Long pickId) {
        Optional<Pick> pickOpt = pickRepository.findById(pickId);
        return pickOpt.map(p -> ResponseEntity.ok(toDto(p)))
                      .orElse(ResponseEntity.notFound().build());
    }

    private PickWithPerformanceDto toDto(Pick pick) {
        Performance perf = pick.getPerformance();
        PickWithPerformanceDto.PerformanceDto perfDto = perf == null ? null :
            new PickWithPerformanceDto.PerformanceDto(
                perf.getId(),
                perf.getStartPrice(),
                perf.getCurrentPrice(),
                perf.getReturn1d(),
                perf.getReturn7d(),
                perf.getReturn30d(),
                perf.getReturn90d(),
                perf.getReturn1y(),
                perf.getReturnYtd(),
                perf.getLastUpdated()
            );

        return new PickWithPerformanceDto(
            pick.getId(),
            pick.getTickerSymbol(),
            pick.getCompanyName(),
            pick.getSignal().name(),
            pick.getConfidenceScore(),
            pick.getExtractionTimestamp(),
            pick.getVideo().getVideoId(),
            pick.getVideo().getTitle(),
            pick.getVideo().getChannel().getChannelId(),
            pick.getVideo().getChannel().getChannelName(),
            perfDto
        );
    }
}
