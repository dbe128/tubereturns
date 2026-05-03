package com.tubereturns.dto;

public record YtbsdStatsDto(
        int totalRuns,
        int successfulRuns,
        int failedRuns,
        Long lastDurationMs,
        Integer lastBatchSize
) {}
