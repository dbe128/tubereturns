package com.tubereturns.service;

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
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
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
    private final PipelineStatusRegistry registry;

    private final ExecutorService extractionExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "extraction-worker");
        t.setDaemon(true);
        return t;
    });

    private final LinkedList<String> pendingVideoIds = new LinkedList<>();
    private final Set<String> queuedVideoIds = new HashSet<>();
    private final Object queueLock = new Object();
    private boolean draining = false;
    @Getter
    private volatile boolean workerRunning = false;
    private volatile boolean halted = false;
    private final java.util.concurrent.atomic.AtomicInteger sessionCount = new java.util.concurrent.atomic.AtomicInteger();

    public int getQueueSize() {
        synchronized (queueLock) {
            return pendingVideoIds.size();
        }
    }

    @PreDestroy
    public void shutdown() {
        extractionExecutor.shutdown();
    }

    public void enqueueAllPending() {
        List<Video> readyVideos = videoRepository.findAllVideosReadyForProcessing();
        if (readyVideos.isEmpty()) {
            if (!workerRunning) {
                registry.markStarted("extraction");
                registry.markFinished("extraction", 0);
            }
            return;
        }
        log.info("Found {} video(s) ready for pick extraction", readyVideos.size());
        readyVideos.forEach(v -> enqueue(v.getVideoId()));
    }

    public void enqueueVideo(String videoId) {
        enqueue(videoId);
    }

    private void enqueue(String videoId) {
        if (halted) {
            log.warn("Extraction is halted due to a fatal error — skipping video {}", videoId);
            return;
        }
        synchronized (queueLock) {
            if (queuedVideoIds.contains(videoId)) {
                return;
            }
            queuedVideoIds.add(videoId);
            pendingVideoIds.addLast(videoId);
            if (!draining) {
                draining = true;
                sessionCount.set(0);
                extractionExecutor.submit(this::drainNext);
            }
        }
    }

    private void drainNext() {
        String videoId;
        synchronized (queueLock) {
            videoId = pendingVideoIds.pollFirst();
            if (videoId == null) {
                workerRunning = false;
                draining = false;
                return;
            }
        }

        if (!workerRunning) {
            workerRunning = true;
            registry.markStarted("extraction");
        }
        int result = 0;
        try {
            result = videoRepository.findByVideoIdWithChannel(videoId).map(this::doProcessVideo).orElse(false) ? 1 : 0;
            sessionCount.incrementAndGet();
            registry.markProgress("extraction", result);
        } catch (PaymentRequiredException e) {
            log.error("Extraction halted: {}", e.getMessage());
            halted = true;
            synchronized (queueLock) {
                pendingVideoIds.clear();
                queuedVideoIds.clear();
                workerRunning = false;
                draining = false;
            }
            registry.markFatalError("extraction", e.getMessage());
            return;
        } finally {
            synchronized (queueLock) {
                queuedVideoIds.remove(videoId);
                if (!pendingVideoIds.isEmpty()) {
                    extractionExecutor.submit(this::drainNext);
                } else {
                    registry.markFinished("extraction", result);
                    workerRunning = false;
                    draining = false;
                }
            }
        }
    }

    private boolean doProcessVideo(Video video) {
        String videoUrl = "https://youtu.be/" + video.getVideoId();
        log.info("Processing video for stock picks: {} ({})", video.getTitle(), videoUrl);

        if (video.getTranscriptText() == null || video.getTranscriptText().isBlank()) {
            log.warn("No transcript text for {} ({})", video.getTitle(), videoUrl);
            video.setProcessingStatus(Video.ProcessingStatus.FAILED);
            videoRepository.save(video);
            return false;
        }

        video.setProcessingStatus(Video.ProcessingStatus.PROCESSING);
        videoRepository.save(video);

        try {
            StockPickExtractionDto extraction = extractStockPicks(video.getVideoId(), video.getTitle(), video.getTranscriptText());
            savePicks(video, extraction);
            video.setProcessingStatus(Video.ProcessingStatus.COMPLETED);
            video.setExtractionModel(aiModelService.getModel());
            if (extraction.externalPositions()) {
                log.info("Video {} contains only external positions — auto-excluding", videoUrl);
                video.setExcluded(true);
                video.setExclusionReason("External Positions");
            }
            videoRepository.save(video);
            advanceLastProcessedAt(video);
            return true;
        } catch (PaymentRequiredException e) {
            video.setProcessingStatus(Video.ProcessingStatus.FAILED);
            videoRepository.save(video);
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract stock picks from video {}: {}", videoUrl, e.getMessage(), e);
            video.setProcessingStatus(Video.ProcessingStatus.FAILED);
            videoRepository.save(video);
            return false;
        }
    }

    private StockPickExtractionDto extractStockPicks(String videoId, String videoTitle, String transcriptText) {
        String videoUrl = "https://youtu.be/" + videoId;
        if (!aiEnabled) {
            log.info("Using mock extraction for {} ({})", videoTitle, videoUrl);
            return createMockExtraction(videoId, transcriptText);
        }

        log.info("Sending transcript to AI for extraction: {} ({})", videoTitle, videoUrl);
        String aiResponse = aiModelService.extractStockPicks(videoTitle, transcriptText);
        log.info("AI response for {} ({}): {}", videoTitle, videoUrl, aiResponse);

        try {
            return objectMapper.readValue(aiResponse, StockPickExtractionDto.class);
        } catch (Exception e) {
            log.error("Failed to parse AI response for {} ({}): {}", videoTitle, videoUrl, e.getMessage());
            throw new RuntimeException("Failed to parse AI response for video " + videoId, e);
        }
    }

    private StockPickExtractionDto createMockExtraction(String videoId, String transcriptText) {
        List<StockPickExtractionDto.PickExtractionDto> extractions = new ArrayList<>();

        extractions.addAll(extractTickersWithRegex(transcriptText));

        if (extractions.isEmpty()) {
            extractions.add(new StockPickExtractionDto.PickExtractionDto("SPY", "SPDR S&P 500 ETF", "BUY"));
        }

        return new StockPickExtractionDto(videoId, extractions, false);
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
        return !ticker.isEmpty() && ticker.length() <= 5 && ticker.matches("[A-Z]+");
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
