package com.tubereturns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record YouTubeChannelDto(
    @NotBlank @Size(max = 255) String channelId,
    @NotBlank @Size(max = 500) String channelName,
    String description,
    Long subscriberCount,
    Integer videoCount
) {}