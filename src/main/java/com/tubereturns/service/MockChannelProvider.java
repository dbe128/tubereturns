package com.tubereturns.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Profile("dev")
public class MockChannelProvider {

    private final List<MockChannelData> channels;

    public MockChannelProvider(ResourcePatternResolver resourcePatternResolver, ObjectMapper objectMapper) {
        this.channels = load(resourcePatternResolver, objectMapper);
    }

    public List<MockChannelData> getChannels() {
        return channels;
    }

    private List<MockChannelData> load(ResourcePatternResolver resolver, ObjectMapper mapper) {
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath:mock-channels/*.json");
        } catch (IOException e) {
            log.warn("Failed to scan mock-channels directory: {}", e.getMessage());
            return List.of();
        }

        List<MockChannelData> result = new ArrayList<>();
        for (Resource resource : resources) {
            try {
                result.add(mapper.readerFor(MockChannelData.class)
                        .without(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                        .readValue(resource.getInputStream()));
                log.info("Loaded mock channel: {}", resource.getFilename());
            } catch (IOException | JacksonException e) {
                log.error("Failed to parse mock channel {}: {}", resource.getFilename(), e.getMessage());
            }
        }
        return result;
    }

    public record MockChannelData(
        String handle,
        String channelName,
        String description,
        List<MockVideoData> videos
    ) {}

    public record MockVideoData(
        String videoId,
        String title,
        Instant publishedAt,
        List<MockPickData> picks
    ) {}

    public record MockPickData(
        String ticker,
        String companyName,
        String signal,
        boolean unknown,
        String currency
    ) {}
}
