package com.tubereturns.dto;

public record PipelineStepStatusDto(
        String step,
        String label,
        String lastStartedAt,
        String lastFinishedAt,
        String nextRunAt,
        boolean running,
        Integer lastRunCount,
        int limit,
        Integer queueSize,
        YtbsdStatsDto ytbsdStats
) {}
