package com.tubereturns.dto;

import java.time.Instant;
import java.util.List;

public record VideoSummaryDto(
    String videoId,
    String title,
    Instant publishedAt,
    Long viewCount,
    String transcriptStatus,
    String extractionStatus,
    String extractionModel,
    List<String> buyPicks,
    boolean excluded,
    String exclusionReason
) {}
