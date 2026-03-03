package com.tubereturns.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DataIngestionOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(DataIngestionOrchestrationService.class);

    private final YouTubeDiscoveryService youTubeDiscoveryService;
    private final TranscriptDownloadService transcriptDownloadService;
    private final StockPickExtractionService stockPickExtractionService;
    private final StockPerformanceService stockPerformanceService;

    public DataIngestionOrchestrationService(YouTubeDiscoveryService youTubeDiscoveryService,
                                           TranscriptDownloadService transcriptDownloadService,
                                           StockPickExtractionService stockPickExtractionService,
                                           StockPerformanceService stockPerformanceService) {
        this.youTubeDiscoveryService = youTubeDiscoveryService;
        this.transcriptDownloadService = transcriptDownloadService;
        this.stockPickExtractionService = stockPickExtractionService;
        this.stockPerformanceService = stockPerformanceService;
    }

    @Scheduled(fixedRate = 3600000) // Run every hour
    public void runFullIngestionPipeline() {
        logger.info("Starting full data ingestion pipeline");

        try {
            // Step 1: Discover new videos from configured channels
            youTubeDiscoveryService.discoverAndProcessChannels();

            // Step 2: Download transcripts for videos without them
            transcriptDownloadService.downloadPendingTranscripts();

            // Step 3: Extract stock picks from transcripts
            stockPickExtractionService.processVideosWithTranscripts();

            // Step 4: Calculate performance for new picks
            stockPerformanceService.calculatePerformanceForNewPicks();

            logger.info("Full data ingestion pipeline completed successfully");

        } catch (Exception e) {
            logger.error("Error during full data ingestion pipeline: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 900000) // Run every 15 minutes
    public void updatePerformanceData() {
        logger.info("Starting performance data update");

        try {
            stockPerformanceService.updateStalePerformanceData();
            logger.info("Performance data update completed successfully");
        } catch (Exception e) {
            logger.error("Error during performance data update: {}", e.getMessage(), e);
        }
    }

    @Async
    public void runIngestionForChannel(String channelId) {
        logger.info("Starting ingestion for specific channel: {}", channelId);

        try {
            // Custom channel-specific ingestion logic could be added here
            runFullIngestionPipeline();
        } catch (Exception e) {
            logger.error("Error during channel-specific ingestion for {}: {}", channelId, e.getMessage(), e);
        }
    }

    public void runManualIngestion() {
        logger.info("Starting manual data ingestion");
        runFullIngestionPipeline();
    }
}