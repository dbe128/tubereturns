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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
@RequiredArgsConstructor
@Service
public class StockPickExtractionService {

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

    public void enqueueForReextraction(String videoId) {
        enqueue(videoId);
    }

    public void enqueueForProcessing(String videoId) {
        enqueue(videoId);
    }

    private void enqueue(String videoId) {
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
            var result = extractStockPicks(video.getVideoId(), video.getTitle(), video.getTranscriptText());
            savePicks(video, result.dto());
            video.setProcessingStatus(Video.ProcessingStatus.COMPLETED);
            video.setExtractionModel(result.model());
            if (result.dto().externalPositions()) {
                log.info("Video {} contains only external positions — auto-excluding", videoUrl);
                video.setExcluded(true);
                video.setExclusionReason("External Positions");
            }
            videoRepository.save(video);
            advanceLastProcessedAt(video);
            return true;
        } catch (Exception e) {
            log.error("Failed to extract stock picks from video {}: {}", videoUrl, e.getMessage(), e);
            video.setProcessingStatus(Video.ProcessingStatus.FAILED);
            videoRepository.save(video);
            return false;
        }
    }

    private record ExtractionWithModel(StockPickExtractionDto dto, String model) {}

    private ExtractionWithModel extractStockPicks(String videoId, String videoTitle, String transcriptText) {
        String videoUrl = "https://youtu.be/" + videoId;
        log.info("Sending transcript to AI for extraction: {} ({})", videoTitle, videoUrl);
        AiModelService.ExtractionResult aiResult = aiModelService.extractStockPicks(videoId, videoTitle, transcriptText);
        log.info("AI response for {} ({}): {}", videoTitle, videoUrl, aiResult.content());

        try {
            return new ExtractionWithModel(objectMapper.readValue(aiResult.content(), StockPickExtractionDto.class), aiResult.model());
        } catch (Exception e) {
            log.error("Failed to parse AI response for {} ({}): {}\nResponse: {}", videoTitle, videoUrl, e.getMessage(), aiResult.content());
            throw new RuntimeException("Failed to parse AI response for video " + videoId, e);
        }
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
            Map<LocalDate, Double> prices = StockPriceService.fetchHistoricalClosePrices(
                    stock.getTickerSymbol(), priceDate.minusDays(7), LocalDate.now());
            int inserted = 0;
            for (Map.Entry<LocalDate, Double> entry : prices.entrySet()) {
                if (!stockPriceRepository.existsByStockIdAndPriceDate(stock.getId(), entry.getKey())) {
                    stockPriceRepository.save(new StockPrice(stock, entry.getKey(), entry.getValue()));
                    inserted++;
                }
            }
            log.info("Saved {} price point(s) for {} from {} to today", inserted, stock.getTickerSymbol(), priceDate.minusDays(7));
        } catch (Exception e) {
            log.warn("Could not fetch prices for {} from {}: {}", stock.getTickerSymbol(), priceDate, e.getMessage());
        }
    }
}
