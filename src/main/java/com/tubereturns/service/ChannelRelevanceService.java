package com.tubereturns.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChannelRelevanceService {

    private final YouTubeApiService youTubeApiService;
    private final AiModelService aiModelService;

    @Value("${tubereturns.channel.relevance-threshold}")
    private int relevanceThreshold;

    public record RelevanceResult(int score, boolean passed) {}

    public RelevanceResult assess(String channelName, String handle) {
        List<String> titles = youTubeApiService.getRecentVideoTitles(handle, 50);
        if (titles.isEmpty()) {
            log.warn("No video titles found for @{} — passing relevance check by default", handle);
            return new RelevanceResult(5, true);
        }
        int score = aiModelService.scoreChannelRelevance(channelName, titles);
        log.info("Channel @{} relevance score: {}/10 (threshold: {})", handle, score, relevanceThreshold);
        return new RelevanceResult(score, score >= relevanceThreshold);
    }
}
