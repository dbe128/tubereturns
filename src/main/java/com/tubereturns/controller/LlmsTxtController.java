package com.tubereturns.controller;

import com.tubereturns.dto.ChannelResponseDto;
import com.tubereturns.repository.CurrencyRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockRepository;
import com.tubereturns.repository.VideoRepository;
import com.tubereturns.service.ChannelListService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@RestController
public class LlmsTxtController {

    private final ChannelListService channelListService;
    private final PickRepository pickRepository;
    private final StockRepository stockRepository;
    private final CurrencyRepository currencyRepository;
    private final VideoRepository videoRepository;

    @GetMapping(value = "/api/llms.txt", produces = "text/plain;charset=UTF-8")
    @Operation(summary = "LLM-readable site summary with live dataset statistics")
    public String llmsTxt() {
        List<ChannelResponseDto> channels = channelListService.getAllChannels();
        List<ChannelResponseDto> scored = channels.stream()
                .filter(c -> c.score1y() != null)
                .toList();
        double avgAlpha1y = scored.stream().mapToDouble(ChannelResponseDto::score1y).average().orElse(0);
        ChannelResponseDto best = scored.stream()
                .max(Comparator.comparingDouble(ChannelResponseDto::score1y))
                .orElse(null);
        String asOf = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));

        return """
                # TubeReturns

                TubeReturns is the first platform that objectively ranks finance YouTubers by the real-money performance of their stock picks, measured against the S&P 500.

                ## Current dataset (as of %s)

                - **%d finance YouTube channels** tracked
                - **%,d stock picks** extracted and measured
                - **%,d unique stocks** across **%d currencies** (all returns converted to USD)
                - **%,d videos** processed
                - Average 1-year alpha across all channels: **%s%%** vs S&P 500
                %s
                ## What this site does

                - Automatically discovers videos from tracked stock-picking YouTube channels
                - Downloads transcripts and uses AI to extract explicit BUY stock picks
                - Fetches historical stock prices from Yahoo Finance from the video's publication date
                - Computes 1-month, 1-year, and 3-year returns for each pick
                - Calculates alpha (outperformance vs SPY) per pick and per channel
                - Ranks channels on a public leaderboard by average alpha

                ## Pages

                - https://tubereturns.com/ — the leaderboard of all tracked channels ranked by alpha
                - https://tubereturns.com/channel/<slug> — per-channel page with every pick and its performance
                - https://tubereturns.com/stock/<ticker> — per-stock page showing which YouTubers picked the stock, when, and how each pick performed
                - https://tubereturns.com/faq — frequently asked questions

                ## What counts as a pick

                Only explicit BUY recommendations for individual publicly-traded stocks are tracked. Crypto, ETFs, bonds, general market commentary, and "external positions" (stocks mentioned but not explicitly recommended) are excluded. The AI extracts the ticker symbol, company name, and uses the video publication date as the entry date.

                ## Key concepts

                - **Alpha**: the channel's average return minus the S&P 500 return over the same period, for picks that have matured
                - **Eligible picks**: picks old enough for the return window to have closed (e.g. a 1-year pick requires the video to be at least 1 year old)
                - **Unresolved picks**: picks where the stock ticker could not be identified — excluded from scoring but shown separately

                ## Data freshness

                - New videos are discovered daily
                - Stock prices and exchange rates are refreshed three times daily
                - Returns are computed from the video's publication date to the current price — no retroactive changes once a window closes
                - The statistics in this file are generated live from the database

                ## Source

                All data is derived from publicly available YouTube transcripts and Yahoo Finance price data. The site is independent and has no commercial relationship with any tracked YouTube channel.

                ## More detail

                See /llms-full.txt for full methodology, example picks, and API access.
                """.formatted(
                asOf,
                channels.size(),
                pickRepository.count(),
                stockRepository.count(),
                currencyRepository.count(),
                videoRepository.count(),
                signed(avgAlpha1y),
                best != null
                        ? "- Best single-channel 1-year alpha: **%s%%** (%s)\n".formatted(signed(best.score1y()), best.channelName())
                        : "");
    }

    private String signed(double value) {
        return String.format(Locale.ENGLISH, "%+.1f", value);
    }
}
