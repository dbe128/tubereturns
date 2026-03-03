package com.tubereturns.dto;

public record PerformanceStatsDto(
    Long totalPicks,
    Double averageReturn30d,
    Double bestReturn30d,
    Double worstReturn30d,
    Double successRate
) {}