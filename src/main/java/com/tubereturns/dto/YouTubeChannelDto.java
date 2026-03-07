package com.tubereturns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record YouTubeChannelDto(
    @NotBlank @Size(max = 255) String youtubeChannelId,
    @NotBlank @Size(max = 500) String channelName,
    String description
) {}