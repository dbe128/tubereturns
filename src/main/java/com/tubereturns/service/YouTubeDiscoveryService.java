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
import java.util.Comparator;
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

    public int discoverAndProcessChannels(int maxVideos) {
        log.info("Starting channel discovery process");

        List<Channel> activeChannels = channelRepository.findAll();
        log.info("Found {} channels to process", activeChannels.size());

        int videosProcessed = 0;
        for (Channel channel : activeChannels) {
            if (videosProcessed >= maxVideos) {
                break;
            }
            try {
                videosProcessed += processChannel(channel, maxVideos - videosProcessed);
            } catch (Exception e) {
                log.error("Error processing channel {}: {}", channel.getHandle(), e.getMessage(), e);
            }
        }

        log.info("Completed channel discovery process, processed {} video(s)", videosProcessed);
        return videosProcessed;
    }

    public int processChannel(Channel channel, int maxVideos) {
        String channelUrl = "https://www.youtube.com/@" + channel.getHandle();
        log.info("Processing channel: {} ({})", channel.getChannelName(), channelUrl);

        if (channel.getThumbnailData() == null) {
            YouTubeApiService.ChannelInfo info = youTubeApiService.resolveChannelInfo(channelUrl);
            if (info != null && info.thumbnailUrl() != null) {
                downloadThumbnail(info.thumbnailUrl(), channel);
                channelRepository.save(channel);
            }
        }

        Instant since = channel.getLastProcessedAt() != null
                ? channel.getLastProcessedAt()
                : Instant.EPOCH;
        log.info("Fetching videos for channel '{}' since {}", channel.getChannelName(), since);
        List<YouTubeVideoDto> recentVideos = youTubeApiService.getRecentVideos(channelUrl, since)
                .stream().limit(maxVideos).toList();

        log.info("Found {} new video(s) for channel '{}'", recentVideos.size(), channel.getChannelName());

        for (YouTubeVideoDto video : recentVideos) {
            processVideo(channel, video);
        }

        recentVideos.stream()
                .map(YouTubeVideoDto::publishedAt)
                .max(Comparator.naturalOrder())
                .ifPresent(latest -> {
                    if (channel.getLastProcessedAt() == null || latest.isAfter(channel.getLastProcessedAt())) {
                        channel.setLastProcessedAt(latest);
                        channelRepository.save(channel);
                    }
                });

        return recentVideos.size();
    }

    public void softDeleteChannel(String handle) {
        channelRepository.findByHandle(handle).ifPresent(channel -> {
            channel.setDeletedAt(java.time.Instant.now());
            channelRepository.save(channel);
        });
    }

    public Channel createOrUpdateChannel(String handle, String channelName, String channelUrl, String thumbnailUrl, String description) {
        return channelRepository.findByHandleIncludingDeleted(handle)
            .map(existing -> {
                existing.setChannelName(channelName);
                if (description != null && !description.isBlank()) {
                    existing.setDescription(description);
                }
                existing.setDeletedAt(null);
                if (existing.getThumbnailData() == null && thumbnailUrl != null && !thumbnailUrl.isBlank()) {
                    downloadThumbnail(thumbnailUrl, existing);
                }
                return channelRepository.save(existing);
            })
            .orElseGet(() -> {
                Channel newChannel = new Channel(handle, channelName);
                newChannel.setDescription(description);
                if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
                    downloadThumbnail(thumbnailUrl, newChannel);
                }
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
