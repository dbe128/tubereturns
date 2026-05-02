package com.tubereturns.dto;

import java.time.Instant;
import java.util.List;

public record VideoSummaryDto(
    String videoId,
    String title,
    Instant publishedAt,
    String transcriptStatus,
    String processingStatus,
    String extractionModel,
    List<String> buyPicks,
    List<String> sellPicks,
    String transcriptText,
    boolean excluded
) {}
