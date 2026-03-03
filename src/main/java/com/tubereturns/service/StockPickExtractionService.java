package com.tubereturns.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tubereturns.dto.StockPickExtractionDto;
import com.tubereturns.model.Pick;
import com.tubereturns.model.Video;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class StockPickExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(StockPickExtractionService.class);

    @Value("${tubereturns.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${tubereturns.extraction.mock-mode:true}")
    private boolean mockMode;

    private final VideoRepository videoRepository;
    private final PickRepository pickRepository;
    private final ObjectMapper objectMapper;
    private final AiModelService aiModelService;

    public StockPickExtractionService(VideoRepository videoRepository,
                                    PickRepository pickRepository,
                                    ObjectMapper objectMapper,
                                    AiModelService aiModelService) {
        this.videoRepository = videoRepository;
        this.pickRepository = pickRepository;
        this.objectMapper = objectMapper;
        this.aiModelService = aiModelService;
    }

    public void processVideosWithTranscripts() {
        List<Video> readyVideos = videoRepository.findVideosReadyForProcessing();
        logger.info("Found {} videos ready for stock pick extraction", readyVideos.size());

        for (Video video : readyVideos) {
            try {
                processVideo(video);
            } catch (Exception e) {
                logger.error("Error processing video {}: {}", video.getVideoId(), e.getMessage(), e);
                video.setProcessingStatus(Video.ProcessingStatus.FAILED);
                videoRepository.save(video);
            }
        }
    }

    public List<Pick> processVideo(Video video) {
        logger.info("Processing video for stock picks: {} ({})", video.getTitle(), video.getVideoId());

        if (video.getTranscriptText() == null || video.getTranscriptText().trim().isEmpty()) {
            logger.warn("Video {} has no transcript text available", video.getVideoId());
            video.setProcessingStatus(Video.ProcessingStatus.FAILED);
            videoRepository.save(video);
            return List.of();
        }

        video.setProcessingStatus(Video.ProcessingStatus.PROCESSING);
        videoRepository.save(video);

        try {
            StockPickExtractionDto extraction = extractStockPicks(video.getVideoId(), video.getTranscriptText());
            List<Pick> createdPicks = savePicks(video, extraction);

            video.setProcessingStatus(Video.ProcessingStatus.COMPLETED);
            videoRepository.save(video);

            logger.info("Successfully extracted {} stock picks from video {}", createdPicks.size(), video.getVideoId());
            return createdPicks;

        } catch (Exception e) {
            logger.error("Failed to extract stock picks from video {}: {}", video.getVideoId(), e.getMessage(), e);
            video.setProcessingStatus(Video.ProcessingStatus.FAILED);
            videoRepository.save(video);
            return List.of();
        }
    }

    private StockPickExtractionDto extractStockPicks(String videoId, String transcriptText) {
        if (!aiEnabled || mockMode) {
            logger.info("Using mock extraction for video: {}", videoId);
            return createMockExtraction(videoId, transcriptText);
        }

        logger.debug("Sending transcript to AI for extraction: {}", videoId);
        String aiResponse = aiModelService.extractStockPicks(transcriptText);

        try {
            return objectMapper.readValue(aiResponse, StockPickExtractionDto.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse AI response for video {}: {}", videoId, e.getMessage());
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

        if (tickerIndex == -1) return "BUY";

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

        for (StockPickExtractionDto.PickExtractionDto pickDto : extraction.extractions()) {
            try {
                Pick.Signal signal = Pick.Signal.valueOf(pickDto.signal().toUpperCase());

                Pick pick = new Pick(video, pickDto.tickerSymbol(), signal);
                pick.setCompanyName(pickDto.companyName());
                pick.setConfidenceScore(BigDecimal.valueOf(0.75));

                Pick savedPick = pickRepository.save(pick);
                savedPicks.add(savedPick);

                logger.debug("Saved stock pick: {} {} for video {}", signal, pickDto.tickerSymbol(), video.getVideoId());

            } catch (IllegalArgumentException e) {
                logger.warn("Invalid signal value '{}' for ticker {} in video {}",
                           pickDto.signal(), pickDto.tickerSymbol(), video.getVideoId());
            }
        }

        return savedPicks;
    }
}