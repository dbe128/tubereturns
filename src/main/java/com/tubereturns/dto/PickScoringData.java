package com.tubereturns.dto;

import java.time.Instant;

public record PickScoringData(
        Long channelId,
        Instant videoPublishedAt,
        boolean stockUnknown,
        Double alpha1m,
        Double alpha1y,
        Double alpha3y
) {}
