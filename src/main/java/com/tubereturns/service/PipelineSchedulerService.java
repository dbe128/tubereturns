package com.tubereturns.service;

import com.tubereturns.repository.VideoRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.IntSupplier;

@Slf4j
@RequiredArgsConstructor
@Service
public class PipelineSchedulerService {

    @Value("${tubereturns.pipeline.discovery.cron}")
    private String discoveryCron;

    @Value("${tubereturns.pipeline.transcript.cron}")
    private String transcriptCron;

    @Value("${tubereturns.pipeline.extraction.cron}")
    private String extractionCron;

    @Value("${tubereturns.pipeline.price-refresh.cron}")
    private String priceRefreshCron;

    @Value("${tubereturns.pipeline.discovery.max-items}")
    private int discoveryMaxItems;

    @Value("${tubereturns.pipeline.transcript.batch-size}")
    private int transcriptMaxItems;

    private final YouTubeDiscoveryService discoveryService;
    private final TranscriptDownloadService transcriptService;
    private final StockPickExtractionService extractionService;
    private final StockPriceRefreshService priceRefreshService;
    private final ExchangeRateService exchangeRateService;
    private final PipelineStatusRegistry registry;
    private final VideoRepository videoRepository;

    @PostConstruct
    void registerSteps() {
        registry.registerStep("discovery", discoveryCron, discoveryMaxItems);
        registry.registerStep("transcript", transcriptCron, transcriptMaxItems);
        registry.registerStep("extraction", extractionCron, 1);
        registry.registerStep("price-refresh", priceRefreshCron, null);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    void resetStaleStatuses() {
        int transcriptReset = videoRepository.resetStaleTranscriptStatuses();
        int processingReset = videoRepository.resetStaleProcessingStatuses();
        if (transcriptReset > 0) {
            log.info("Reset {} video(s) from DOWNLOADING → PENDING (transcript)", transcriptReset);
        }
        if (processingReset > 0) {
            log.info("Reset {} video(s) from PROCESSING → PENDING (extraction)", processingReset);
        }
    }

    @Scheduled(cron = "${tubereturns.pipeline.discovery.cron}")
    public void runDiscovery() {
        if (registry.isRunning("discovery")) {
            log.warn("Pipeline step 'discovery' is already running, skipping");
            return;
        }
        discoveryService.scheduleDiscovery(discoveryMaxItems);
    }

    @Scheduled(cron = "${tubereturns.pipeline.transcript.cron}")
    public void runTranscript() {
        if (registry.isRunning("transcript")) {
            log.warn("Pipeline step 'transcript' is already running, skipping");
            return;
        }
        transcriptService.downloadPendingTranscripts(transcriptMaxItems);
    }

    @Scheduled(cron = "${tubereturns.pipeline.extraction.cron}")
    public void runExtraction() {
        if (registry.isRunning("extraction")) {
            log.warn("Pipeline step 'extraction' is already running, skipping");
            return;
        }
        extractionService.enqueueAllPending();
    }

    @Async
    public void triggerDiscovery() {
        discoveryService.scheduleDiscovery(discoveryMaxItems);
    }

    @Async
    public void triggerTranscript() {
        if (registry.isRunning("transcript")) {
            log.warn("Pipeline step 'transcript' is already running, skipping");
            return;
        }
        transcriptService.downloadPendingTranscripts(transcriptMaxItems);
    }

    @Async
    public void triggerExtraction() {
        extractionService.enqueueAllPending();
    }

    @Scheduled(cron = "${tubereturns.pipeline.price-refresh.cron}")
    public void runPriceRefresh() {
        runStep("price-refresh", () -> {
            int prices = priceRefreshService.refreshAllPrices();
            exchangeRateService.refreshRecentRates();
            return prices;
        });
    }

    @Async
    public void triggerPriceRefresh() {
        runStep("price-refresh", () -> {
            int prices = priceRefreshService.refreshAllPrices();
            exchangeRateService.refreshRecentRates();
            return prices;
        });
    }

    private void runStep(String step, IntSupplier task) {
        if (registry.isRunning(step)) {
            log.warn("Pipeline step '{}' is already running, skipping", step);
            return;
        }
        log.info("Starting pipeline step '{}'", step);
        registry.markStarted(step);
        try {
            int count = task.getAsInt();
            log.info("Pipeline step '{}' completed, processed {} items", step, count);
            registry.markFinished(step, count);
        } catch (Exception e) {
            log.error("Pipeline step '{}' failed: {}", step, e.getMessage(), e);
            registry.markFinished(step, 0);
        }
    }
}
