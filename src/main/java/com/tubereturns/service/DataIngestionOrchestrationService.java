package com.tubereturns.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class DataIngestionOrchestrationService {

    private final YouTubeDiscoveryService youTubeDiscoveryService;
    private final TranscriptDownloadService transcriptDownloadService;
    private final StockPickExtractionService stockPickExtractionService;

    @Scheduled(fixedRate = 3600000, initialDelay = 10000) // 10s delay on startup, then every hour
    public void runFullIngestionPipeline() {
        log.info("Starting full data ingestion pipeline");

        try {
            // Step 1: Discover new videos from configured channels
            youTubeDiscoveryService.discoverAndProcessChannels();

            // Step 2: Download transcripts for videos without them
            transcriptDownloadService.downloadPendingTranscripts();

            // Step 3: Extract stock picks from transcripts
            stockPickExtractionService.processVideosWithTranscripts();

            log.info("Full data ingestion pipeline completed successfully");

        } catch (Exception e) {
            log.error("Error during full data ingestion pipeline: {}", e.getMessage(), e);
        }
    }

    @Async
    public void runIngestionForChannel(String channelId) {
        log.info("Starting ingestion for specific channel: {}", channelId);

        try {
            runFullIngestionPipeline();
        } catch (Exception e) {
            log.error("Error during channel-specific ingestion for {}: {}", channelId, e.getMessage(), e);
        }
    }

    public void runManualIngestion() {
        log.info("Starting manual data ingestion");
        runFullIngestionPipeline();
    }
}
