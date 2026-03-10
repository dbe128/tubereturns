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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class YouTubeDiscoveryService {

    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final YouTubeApiService youTubeApiService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

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

        if (channel.getThumbnailData() == null) {
            YouTubeApiService.ChannelInfo info = youTubeApiService.resolveChannelInfo(channel.getChannelUrl());
            if (info != null && info.thumbnailUrl() != null) {
                downloadThumbnail(info.thumbnailUrl(), channel);
                channelRepository.save(channel);
            }
        }

        Instant since = channel.getLastProcessedAt() != null
                ? channel.getLastProcessedAt()
                : Instant.EPOCH;
        log.info("Fetching videos for channel '{}' since {}", channel.getChannelName(), since);
        List<YouTubeVideoDto> recentVideos = youTubeApiService.getRecentVideos(channel.getChannelUrl(), since);

        log.info("Found {} new video(s) for channel '{}'", recentVideos.size(), channel.getChannelName());

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

    private void downloadThumbnail(String url, Channel channel) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            channel.setThumbnailData(response.body());
            channel.setThumbnailContentType(
                    response.headers().firstValue("Content-Type").orElse("image/jpeg"));
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to download thumbnail from {}: {}", url, e.getMessage());
        }
    }

    private void processVideo(Channel channel, YouTubeVideoDto videoDto) {
        log.debug("Processing video: {} for channel {}", videoDto.title(), channel.getChannelName());

        if (videoRepository.existsByVideoId(videoDto.videoId())) {
            log.debug("Video {} already exists, skipping", "https://youtu.be/" + videoDto.videoId());
            return;
        }

        Video video = new Video(videoDto.videoId(), channel, videoDto.title(), videoDto.publishedAt());
        video.setDurationSeconds(videoDto.durationSeconds());
        video.setViewCount(videoDto.viewCount());
        video.setLikeCount(videoDto.likeCount());

        videoRepository.save(video);
        log.info("Created new video: {} ({})", videoDto.title(), "https://youtu.be/" + videoDto.videoId());
    }
}
