package com.tubereturns.dto;

public record ChannelSearchResultDto(
        String handle,
        String channelName,
        String channelUrl,
        String thumbnailUrl,
        String description,
        Long subscriberCount,
        Long videoCount,
        String channelCreatedAt,
        String channelId
) {}
