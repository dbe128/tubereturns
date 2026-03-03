package com.tubereturns.controller;

import com.tubereturns.service.DataIngestionOrchestrationService;
import com.tubereturns.service.YouTubeDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Administrative operations and manual triggers")
public class AdminController {

    private final DataIngestionOrchestrationService orchestrationService;
    private final YouTubeDiscoveryService discoveryService;

    public AdminController(DataIngestionOrchestrationService orchestrationService,
                          YouTubeDiscoveryService discoveryService) {
        this.orchestrationService = orchestrationService;
        this.discoveryService = discoveryService;
    }

    @PostMapping("/ingestion/run")
    @Operation(summary = "Run manual data ingestion", description = "Trigger the full data ingestion pipeline manually")
    public ResponseEntity<String> runManualIngestion() {
        orchestrationService.runManualIngestion();
        return ResponseEntity.ok("Data ingestion pipeline started");
    }

    @PostMapping("/channels/{channelId}/add")
    @Operation(summary = "Add new channel", description = "Add a new YouTube channel for monitoring")
    public ResponseEntity<String> addChannel(
            @Parameter(description = "YouTube channel ID") @PathVariable String channelId,
            @Parameter(description = "Channel name") @RequestParam String channelName) {
        discoveryService.createOrUpdateChannel(channelId, channelName);
        return ResponseEntity.ok("Channel added successfully");
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the application is running")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("TubeReturns is running");
    }
}