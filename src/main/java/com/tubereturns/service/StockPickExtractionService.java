package com.tubereturns.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tubereturns.dto.StockPickExtractionDto;
import com.tubereturns.model.Pick;
import com.tubereturns.model.Stock;
import com.tubereturns.model.StockPrice;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockPriceRepository;
import com.tubereturns.repository.StockRepository;
import com.tubereturns.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class StockPickExtractionService {

    @Value("${tubereturns.ai.enabled:false}")
    private boolean aiEnabled;

    private final VideoRepository videoRepository;
    private final PickRepository pickRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;
    private final AiModelService aiModelService;

    public List<Pick> reprocessVideo(String youtubeVideoId) {
        Video video = videoRepository.findByVideoId(youtubeVideoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + youtubeVideoId));
        pickRepository.deleteByVideoId(video.getId());
        video.setProcessingStatus(Video.ProcessingStatus.PENDING);
        videoRepository.save(video);
        return processVideo(video);
    }

    public void processVideosWithTranscripts() {
        List<Video> readyVideos = videoRepository.findVideosReadyForProcessing();
        log.info("Found {} videos ready for stock pick extraction", readyVideos.size());

        for (Video video : readyVideos) {
            try {
                processVideo(video);
            } catch (Exception e) {
                log.error("Error processing video {}: {}", "https://youtu.be/" + video.getVideoId(), e.getMessage(), e);
                video.setProcessingStatus(Video.ProcessingStatus.FAILED);
                videoRepository.save(video);
            }
        }
    }

    public List<Pick> processVideo(Video video) {
        log.info("Processing video for stock picks: {} ({})", video.getTitle(), "https://youtu.be/" + video.getVideoId());

        if (video.getTranscriptText() == null || video.getTranscriptText().trim().isEmpty()) {
            log.warn("Video {} has no transcript text available", "https://youtu.be/" + video.getVideoId());
            video.setProcessingStatus(Video.ProcessingStatus.FAILED);
            videoRepository.save(video);
            return List.of();
        }

        video.setProcessingStatus(Video.ProcessingStatus.PROCESSING);
        videoRepository.save(video);

        try {
            StockPickExtractionDto extraction = extractStockPicks(video.getVideoId(), video.getTitle(), video.getTranscriptText());
            List<Pick> createdPicks = savePicks(video, extraction);

            video.setProcessingStatus(Video.ProcessingStatus.COMPLETED);
            video.setExtractionModel(aiModelService.getModel());
            videoRepository.save(video);

            advanceLastProcessedAt(video);

            return createdPicks;

        } catch (Exception e) {
            log.error("Failed to extract stock picks from video {}: {}", "https://youtu.be/" + video.getVideoId(), e.getMessage(), e);
            video.setProcessingStatus(Video.ProcessingStatus.FAILED);
            videoRepository.save(video);
            return List.of();
        }
    }

    private StockPickExtractionDto extractStockPicks(String videoId, String videoTitle, String transcriptText) {
        if (!aiEnabled) {
            log.info("Using mock extraction for video: {}", "https://youtu.be/" + videoId);
            return createMockExtraction(videoId, transcriptText);
        }

        log.info("Sending transcript to AI for extraction: {}", "https://youtu.be/" + videoId);
        String aiResponse = aiModelService.extractStockPicks(videoTitle, transcriptText);
        log.info("AI response for video {}: {}", "https://youtu.be/" + videoId, aiResponse);

        try {
            return objectMapper.readValue(aiResponse, StockPickExtractionDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI response for video {}: {}", "https://youtu.be/" + videoId, e.getMessage());
            return createMockExtraction(videoId, transcriptText);
        }
    }

    private StockPickExtractionDto createMockExtraction(String videoId, String transcriptText) {
        List<StockPickExtractionDto.PickExtractionDto> extractions = new ArrayList<>();

        extractions.addAll(extractTickersWithRegex(transcriptText));

        if (extractions.isEmpty()) {
            extractions.add(new StockPickExtractionDto.PickExtractionDto("SPY", "SPDR S&P 500 ETF", "BUY"));
        }

        return new StockPickExtractionDto(videoId, extractions);
    }

    private List<StockPickExtractionDto.PickExtractionDto> extractTickersWithRegex(String text) {
        List<StockPickExtractionDto.PickExtractionDto> picks = new ArrayList<>();

        Pattern buyPattern = Pattern.compile("\\b(?:buy|buying|purchased?|long)\\s+(?:stock\\s+)?([A-Z]{1,5})\\b", Pattern.CASE_INSENSITIVE);
        Pattern sellPattern = Pattern.compile("\\b(?:sell|selling|sold|short)\\s+(?:stock\\s+)?([A-Z]{1,5})\\b", Pattern.CASE_INSENSITIVE);
        Pattern tickerPattern = Pattern.compile("\\b(AAPL|TSLA|MSFT|GOOGL?|AMZN|META|NVDA|CRM|NFLX|UBER)\\b");

        Matcher buyMatcher = buyPattern.matcher(text);
        while (buyMatcher.find()) {
            String ticker = buyMatcher.group(1);
            if (isValidTicker(ticker)) {
                picks.add(new StockPickExtractionDto.PickExtractionDto(ticker, getCompanyName(ticker), "BUY"));
            }
        }

        Matcher sellMatcher = sellPattern.matcher(text);
        while (sellMatcher.find()) {
            String ticker = sellMatcher.group(1);
            if (isValidTicker(ticker)) {
                picks.add(new StockPickExtractionDto.PickExtractionDto(ticker, getCompanyName(ticker), "SELL"));
            }
        }

        Matcher tickerMatcher = tickerPattern.matcher(text);
        while (tickerMatcher.find()) {
            String ticker = tickerMatcher.group(1);
            String signal = determineSignalFromContext(text, ticker);
            if (signal != null && !containsTicker(picks, ticker)) {
                picks.add(new StockPickExtractionDto.PickExtractionDto(ticker, getCompanyName(ticker), signal));
            }
        }

        return picks;
    }

    private boolean isValidTicker(String ticker) {
        return ticker.length() >= 1 && ticker.length() <= 5 && ticker.matches("[A-Z]+");
    }

    private boolean containsTicker(List<StockPickExtractionDto.PickExtractionDto> picks, String ticker) {
        return picks.stream().anyMatch(pick -> pick.tickerSymbol().equals(ticker));
    }

    private String determineSignalFromContext(String text, String ticker) {
        String lowerText = text.toLowerCase();
        int tickerIndex = lowerText.indexOf(ticker.toLowerCase());

        if (tickerIndex == -1) {
            return "BUY";
        }

        String contextBefore = lowerText.substring(Math.max(0, tickerIndex - 100), tickerIndex);
        String contextAfter = lowerText.substring(tickerIndex, Math.min(lowerText.length(), tickerIndex + 100));
        String fullContext = contextBefore + " " + contextAfter;

        if (fullContext.contains("sell") || fullContext.contains("short") || fullContext.contains("avoid")) {
            return "SELL";
        }

        if (fullContext.contains("buy") || fullContext.contains("bullish") || fullContext.contains("long")) {
            return "BUY";
        }

        return "BUY";
    }

    private String getCompanyName(String ticker) {
        return switch (ticker.toUpperCase()) {
            case "AAPL" -> "Apple Inc.";
            case "TSLA" -> "Tesla Inc.";
            case "MSFT" -> "Microsoft Corporation";
            case "GOOGL", "GOOG" -> "Alphabet Inc.";
            case "AMZN" -> "Amazon.com Inc.";
            case "META" -> "Meta Platforms Inc.";
            case "NVDA" -> "NVIDIA Corporation";
            case "CRM" -> "Salesforce Inc.";
            case "NFLX" -> "Netflix Inc.";
            case "UBER" -> "Uber Technologies Inc.";
            case "SPY" -> "SPDR S&P 500 ETF";
            default -> null;
        };
    }

    private List<Pick> savePicks(Video video, StockPickExtractionDto extraction) {
        List<Pick> savedPicks = new ArrayList<>();
        LocalDate priceDate = video.getPublishedAt().atZone(ZoneOffset.UTC).toLocalDate();

        for (StockPickExtractionDto.PickExtractionDto pickDto : extraction.extractions()) {
            try {
                Pick.Signal signal = Pick.Signal.valueOf(pickDto.signal().toUpperCase());

                Stock stock = stockRepository.findByTickerSymbol(pickDto.tickerSymbol().toUpperCase())
                        .orElseGet(() -> stockRepository.save(new Stock(pickDto.tickerSymbol(), pickDto.companyName())));

                savedPicks.add(pickRepository.save(new Pick(video, stock, signal)));

                fetchAndSaveStockPrice(stock, priceDate);

            } catch (IllegalArgumentException e) {
                log.warn("Invalid signal value '{}' for ticker {} in video {}",
                           pickDto.signal(), pickDto.tickerSymbol(), "https://youtu.be/" + video.getVideoId());
            }
        }

        if (!savedPicks.isEmpty()) {
            String picksSummary = savedPicks.stream()
                    .map(p -> p.getSignal() + " " + p.getStock().getTickerSymbol())
                    .collect(java.util.stream.Collectors.joining(", "));
            log.info("Extracted {} pick(s) from {} — [{}]",
                    savedPicks.size(), "https://youtu.be/" + video.getVideoId(), picksSummary);
        }

        return savedPicks;
    }

    private void advanceLastProcessedAt(Video video) {
        var channel = video.getChannel();
        if (channel.getLastProcessedAt() == null || video.getPublishedAt().isAfter(channel.getLastProcessedAt())) {
            channel.setLastProcessedAt(video.getPublishedAt());
            channelRepository.save(channel);
            log.info("Advanced last_processed_at for channel '{}' to {}", channel.getChannelName(), video.getPublishedAt());
        }
    }

    private void fetchAndSaveStockPrice(Stock stock, LocalDate priceDate) {
        try {
            if (stockPriceRepository.existsByStockIdAndPriceDate(stock.getId(), priceDate)) {
                return;
            }
            double closePrice = StockPriceService.getClosePrice(stock.getTickerSymbol(), priceDate);
            stockPriceRepository.save(new StockPrice(stock, priceDate, closePrice));
            log.info("Saved price ${} for {} on {}", closePrice, stock.getTickerSymbol(), priceDate);
        } catch (Exception e) {
            log.warn("Could not fetch price for {} on {}: {}", stock.getTickerSymbol(), priceDate, e.getMessage());
        }
    }
}
