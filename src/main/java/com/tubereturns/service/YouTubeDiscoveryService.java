package com.tubereturns.service;

import com.tubereturns.dto.YouTubeVideoDto;
import com.tubereturns.model.Channel;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class YouTubeDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeDiscoveryService.class);

    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final YouTubeApiService youTubeApiService;

    public YouTubeDiscoveryService(ChannelRepository channelRepository, VideoRepository videoRepository, YouTubeApiService youTubeApiService) {
        this.channelRepository = channelRepository;
        this.videoRepository = videoRepository;
        this.youTubeApiService = youTubeApiService;
    }

    public void discoverAndProcessChannels() {
        logger.info("Starting channel discovery process");

        List<Channel> activeChannels = channelRepository.findByIsActiveTrue();
        logger.info("Found {} active channels to process", activeChannels.size());

        for (Channel channel : activeChannels) {
            try {
                processChannel(channel);
            } catch (Exception e) {
                logger.error("Error processing channel {}: {}", channel.getChannelId(), e.getMessage(), e);
            }
        }

        logger.info("Completed channel discovery process");
    }

    public void processChannel(Channel channel) {
        logger.info("Processing channel: {} ({})", channel.getChannelName(), channel.getChannelId());

        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<YouTubeVideoDto> recentVideos = youTubeApiService.getRecentVideos(channel.getChannelUrl(), since);

        logger.info("Found {} recent videos for channel {}", recentVideos.size(), channel.getChannelName());

        for (YouTubeVideoDto video : recentVideos) {
            processVideo(channel, video);
        }
    }

    public Channel createOrUpdateChannel(String channelId, String channelName) {
        return channelRepository.findByChannelId(channelId)
            .map(existing -> {
                existing.setChannelName(channelName);
                return channelRepository.save(existing);
            })
            .orElseGet(() -> {
                Channel newChannel = new Channel(channelId, channelName);
                return channelRepository.save(newChannel);
            });
    }

    private void processVideo(Channel channel, YouTubeVideoDto videoDto) {
        logger.debug("Processing video: {} for channel {}", videoDto.title(), channel.getChannelName());

        // Check if video already exists
        if (videoRepository.existsByVideoId(videoDto.videoId())) {
            logger.debug("Video {} already exists, skipping", videoDto.videoId());
            return;
        }

        // Create and save new video
        Video video = new Video(videoDto.videoId(), channel, videoDto.title(), videoDto.publishedAt());
        video.setDescription(videoDto.description());
        video.setDurationSeconds(videoDto.durationSeconds());
        video.setViewCount(videoDto.viewCount());
        video.setLikeCount(videoDto.likeCount());

        videoRepository.save(video);
        logger.info("Created new video: {} ({})", videoDto.title(), videoDto.videoId());
    }
}