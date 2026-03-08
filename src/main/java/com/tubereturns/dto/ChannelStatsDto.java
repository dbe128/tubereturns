package com.tubereturns.dto;

import java.util.List;

public record ChannelStatsDto(
    String youtubeChannelId,
    String channelName,
    long totalVideos,
    long processedVideos,
    List<String> buyPicks,
    List<String> sellPicks
) {}
