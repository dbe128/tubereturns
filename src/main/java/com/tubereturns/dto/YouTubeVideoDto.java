package com.tubereturns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record YouTubeVideoDto(
    @NotBlank @Size(max = 255) String videoId,
    @NotBlank String title,
    String description,
    @NotNull Instant publishedAt,
    Integer durationSeconds,
    Long viewCount,
    Long likeCount
) {}