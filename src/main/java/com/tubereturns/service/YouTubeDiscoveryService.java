package com.tubereturns.service;

import com.tubereturns.dto.YouTubeVideoDto;
import com.tubereturns.model.Channel;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.VideoRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RequiredArgsConstructor
@Service
public class YouTubeDiscoveryService {

    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final YouTubeApiService youTubeApiService;
    private final PipelineStatusRegistry registry;
    private final TranscriptDownloadService transcriptDownloadService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ExecutorService discoveryExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "discovery-worker");
        t.setDaemon(true);
        return t;
    });

    private final Object queueLock = new Object();
    private boolean draining = false;
    private boolean pendingRun = false;

    public int getQueueSize() {
        synchronized (queueLock) {
            return pendingRun ? 1 : 0;
        }
    }

    public void scheduleDiscovery(int maxVideos) {
        synchronized (queueLock) {
            if (!draining) {
                draining = true;
                discoveryExecutor.submit(() -> drainAndRun(maxVideos));
                log.info("Discovery scheduled");
            } else if (!pendingRun) {
                pendingRun = true;
                log.info("Discovery already running, queued one pending run");
            } else {
                log.info("Discovery already running with a pending run, skipping duplicate");
            }
        }
    }

    private void drainAndRun(int maxVideos) {
        registry.markStarted("discovery");
        int count = 0;
        try {
            count = discoverAndProcessChannels(maxVideos);
        } catch (Exception e) {
            log.error("Discovery failed: {}", e.getMessage(), e);
        } finally {
            registry.markFinished("discovery", count);
            if (count > 0) {
                transcriptDownloadService.downloadPendingTranscripts();
            }
            synchronized (queueLock) {
                if (pendingRun) {
                    pendingRun = false;
                    discoveryExecutor.submit(() -> drainAndRun(maxVideos));
                } else {
                    draining = false;
                }
            }
        }
    }

    @Transactional
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

    @Transactional
    public int processChannel(Channel channel, int maxVideos) {
        if (channel.getHandle().startsWith("mock-")) {
            log.debug("Skipping mock channel: {}", channel.getHandle());
            return 0;
        }
        String channelUrl = "https://www.youtube.com/@" + channel.getHandle();
        log.info("Processing channel: {} ({})", channel.getChannelName(), channelUrl);

        YouTubeApiService.ChannelInfo info = youTubeApiService.resolveChannelInfo(channelUrl);
        if (info == null) {
            log.warn("Could not resolve channel info for: {}", channelUrl);
            return 0;
        }

        boolean needsSave = false;
        if (channel.getThumbnailData() == null && info.thumbnailUrl() != null) {
            downloadThumbnail(info.thumbnailUrl(), channel);
            needsSave = true;
        }
        if (info.subscriberCount() != null && !info.subscriberCount().equals(channel.getSubscriberCount())) {
            channel.setSubscriberCount(info.subscriberCount());
            needsSave = true;
        }
        if (needsSave) {
            channelRepository.save(channel);
        }

        Instant since = channel.getLastProcessedAt() != null
                ? channel.getLastProcessedAt()
                : Instant.EPOCH;
        log.info("Fetching videos for channel '{}' since {}", channel.getChannelName(), since);
        List<YouTubeVideoDto> recentVideos = youTubeApiService.getVideosFromPlaylist(info.uploadsPlaylistId(), since)
                .stream().limit(maxVideos).toList();

        log.info("Found {} new video(s) for channel '{}'", recentVideos.size(), channel.getChannelName());

        for (YouTubeVideoDto video : recentVideos) {
            processVideo(channel, video);
        }

        boolean channelChanged = false;
        if (recentVideos.size() < maxVideos && !channel.isDiscoveryComplete()) {
            channel.setDiscoveryComplete(true);
            channelChanged = true;
            log.info("Channel '{}' discovery complete", channel.getChannelName());
        }

        recentVideos.stream()
                .map(YouTubeVideoDto::publishedAt)
                .max(Comparator.naturalOrder())
                .ifPresent(latest -> {
                    if (channel.getLastProcessedAt() == null || latest.isAfter(channel.getLastProcessedAt())) {
                        channel.setLastProcessedAt(latest);
                    }
                });

        if (channelChanged || !recentVideos.isEmpty()) {
            channelRepository.save(channel);
        }

        return recentVideos.size();
    }

    @Transactional
    public void softDeleteChannel(String handle) {
        channelRepository.findByHandle(handle).ifPresent(channel -> {
            channel.setDeletedAt(java.time.Instant.now());
            channelRepository.save(channel);
        });
    }

    @Transactional
    public Channel createOrUpdateChannel(String handle, String channelName, String channelUrl, String thumbnailUrl, String description, Long subscriberCount) {
        return channelRepository.findByHandleIncludingDeleted(handle)
            .map(existing -> {
                existing.setChannelName(channelName);
                existing.setNameSlug(Channel.nameSlugFor(channelName));
                if (description != null && !description.isBlank()) {
                    existing.setDescription(description);
                }
                existing.setDeletedAt(null);
                if (existing.getThumbnailData() == null && thumbnailUrl != null && !thumbnailUrl.isBlank()) {
                    downloadThumbnail(thumbnailUrl, existing);
                }
                if (subscriberCount != null) {
                    existing.setSubscriberCount(subscriberCount);
                }
                return channelRepository.save(existing);
            })
            .orElseGet(() -> {
                Channel newChannel = new Channel(handle, channelName);
                newChannel.setDescription(description);
                newChannel.setSubscriberCount(subscriberCount);
                if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
                    downloadThumbnail(thumbnailUrl, newChannel);
                }
                return channelRepository.save(newChannel);
            });
    }

    @PreDestroy
    public void shutdown() {
        discoveryExecutor.shutdown();
    }

    private void downloadThumbnail(String url, Channel channel) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(30))
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
