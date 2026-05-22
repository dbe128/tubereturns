package com.tubereturns.service;

import com.tubereturns.dto.ChannelResponseDto;
import com.tubereturns.model.Channel;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChannelListService {

    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final PickRepository pickRepository;
    private final PickPerformanceService pickPerformanceService;

    @Lazy
    @Autowired
    private ChannelListService self;

    @Cacheable("allChannels")
    public List<ChannelResponseDto> getAllChannels() {
        return buildChannelList();
    }

    @CacheEvict(value = "allChannels", allEntries = true)
    public void evictAllChannels() {
        log.debug("Evicted allChannels cache");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        log.info("Pre-warming allChannels cache");
        self.getAllChannels();
    }

    private List<ChannelResponseDto> buildChannelList() {
        List<Channel> channels = channelRepository.findAll();

        Map<Long, Long> totalByChannel = new HashMap<>();
        videoRepository.countAllGroupedByChannelId()
                .forEach(row -> totalByChannel.put((Long) row[0], (Long) row[1]));

        Map<Long, Long> processedByChannel = new HashMap<>();
        videoRepository.countProcessedGroupedByChannelId()
                .forEach(row -> processedByChannel.put((Long) row[0], (Long) row[1]));

        Map<Long, PickPerformanceService.ChannelScoreResult> scoresByChannel =
                pickPerformanceService.computeScoresForAllChannels(pickRepository.findAllPicksForScoring());

        return channels.stream()
                .map(c -> toDto(c,
                        totalByChannel.getOrDefault(c.getId(), 0L),
                        processedByChannel.getOrDefault(c.getId(), 0L),
                        scoresByChannel.getOrDefault(c.getId(), PickPerformanceService.ChannelScoreResult.empty())))
                .toList();
    }

    private ChannelResponseDto toDto(Channel channel, long totalVideos, long processedVideos,
            PickPerformanceService.ChannelScoreResult score) {
        return new ChannelResponseDto(
                channel.getId(),
                channel.getHandle(),
                channel.getChannelName(),
                channel.getDescription(),
                channel.getThumbnailData() != null,
                channel.getCreatedAt(),
                channel.getUpdatedAt(),
                channel.getSubscriberCount(),
                channel.isDiscoveryComplete(),
                totalVideos,
                processedVideos,
                score.score1m(), score.eligible1m(), score.unresolved1m(),
                score.score1y(), score.eligible1y(), score.unresolved1y(),
                score.score3y(), score.eligible3y(), score.unresolved3y());
    }
}
