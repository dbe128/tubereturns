package com.tubereturns.dto;

public record AiModelStatusDto(
        int currentIndex,
        String currentModel,
        String model0ResetAt
) {}
