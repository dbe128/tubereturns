package com.tubereturns.dto;

public record BlockedChannelDto(
        Long id,
        String handle,
        String youtubeChannelId,
        String reason,
        String createdAt
) {}
