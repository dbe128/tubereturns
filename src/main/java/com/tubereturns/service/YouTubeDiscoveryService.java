package com.tubereturns.service;

import com.tubereturns.dto.YouTubeVideoDto;
import com.tubereturns.model.Channel;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class YouTubeDiscoveryService {

    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final YouTubeApiService youTubeApiService;

    public void discoverAndProcessChannels() {
        log.info("Starting channel discovery process");

        List<Channel> activeChannels = channelRepository.findAll();
        log.info("Found {} channels to process", activeChannels.size());

        for (Channel channel : activeChannels) {
            try {
                processChannel(channel);
            } catch (Exception e) {
                log.error("Error processing channel {}: {}", channel.getYoutubeChannelId(), e.getMessage(), e);
            }
        }

        log.info("Completed channel discovery process");
    }

    public void processChannel(Channel channel) {
        log.info("Processing channel: {} ({})", channel.getChannelName(), channel.getYoutubeChannelId());

        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<YouTubeVideoDto> recentVideos = youTubeApiService.getRecentVideos(channel.getChannelUrl(), since);

        log.info("Found {} recent videos for channel {}", recentVideos.size(), channel.getChannelName());

        for (YouTubeVideoDto video : recentVideos) {
            processVideo(channel, video);
        }
    }

    public Channel createOrUpdateChannel(String channelId, String channelName) {
        return channelRepository.findByYoutubeChannelId(channelId)
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
        log.debug("Processing video: {} for channel {}", videoDto.title(), channel.getChannelName());

        if (videoRepository.existsByVideoId(videoDto.videoId())) {
            log.debug("Video {} already exists, skipping", "https://youtu.be/" + videoDto.videoId());
            return;
        }

        Video video = new Video(videoDto.videoId(), channel, videoDto.title(), videoDto.publishedAt());
        video.setDescription(videoDto.description());
        video.setDurationSeconds(videoDto.durationSeconds());
        video.setViewCount(videoDto.viewCount());
        video.setLikeCount(videoDto.likeCount());

        videoRepository.save(video);
        log.info("Created new video: {} ({})", videoDto.title(), "https://youtu.be/" + videoDto.videoId());
    }
}
