package com.tubereturns.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.tubereturns.dto.YouTubeVideoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class YouTubeApiService {

    @Value("${tubereturns.youtube.api-key:}")
    private String apiKey;

    @Value("${tubereturns.youtube.enabled:false}")
    private boolean enabled;

    public List<YouTubeVideoDto> getRecentVideos(String channelUrl, Instant since) {
        if (!enabled || channelUrl == null || channelUrl.isBlank()) {
            log.warn("YouTube API disabled or no channel URL — skipping video discovery");
            return List.of();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.error("YOUTUBE_API_KEY is not set — cannot discover videos for {}", channelUrl);
            return List.of();
        }

        try {
            YouTube youtube = buildClient();
            String uploadsPlaylistId = resolveUploadsPlaylistId(youtube, channelUrl);
            if (uploadsPlaylistId == null) {
                log.warn("Could not resolve uploads playlist for: {}", channelUrl);
                return List.of();
            }

            List<String> videoIds = fetchVideoIds(youtube, uploadsPlaylistId, since);
            return fetchVideoDetails(youtube, videoIds).stream()
                    .sorted(Comparator.comparing(YouTubeVideoDto::publishedAt))
                    .toList();

        } catch (Exception e) {
            log.error("Failed to discover videos for {}: {}", channelUrl, e.getMessage(), e);
            return List.of();
        }
    }

    private YouTube buildClient() throws GeneralSecurityException, IOException {
        return new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {}
        ).setApplicationName("tubereturns").build();
    }

    private String resolveUploadsPlaylistId(YouTube youtube, String channelUrl) throws IOException {
        YouTube.Channels.List request = youtube.channels()
                .list(List.of("contentDetails"))
                .setKey(apiKey);

        if (channelUrl.contains("/@")) {
            String handle = channelUrl.substring(channelUrl.lastIndexOf("/@") + 2);
            if (handle.contains("/")) {
                handle = handle.substring(0, handle.indexOf("/"));
            }
            request.set("forHandle", handle);
        } else if (channelUrl.contains("/channel/")) {
            String channelId = channelUrl.substring(channelUrl.lastIndexOf("/channel/") + 9);
            if (channelId.contains("/")) {
                channelId = channelId.substring(0, channelId.indexOf("/"));
            }
            request.setId(List.of(channelId));
        } else {
            log.warn("Unsupported channel URL format: {}", channelUrl);
            return null;
        }

        var response = request.execute();
        if (response.getItems() == null || response.getItems().isEmpty()) {
            return null;
        }

        return response.getItems().get(0).getContentDetails().getRelatedPlaylists().getUploads();
    }

    private List<String> fetchVideoIds(YouTube youtube, String uploadsPlaylistId, Instant since) throws IOException {
        List<String> videoIds = new ArrayList<>();
        String pageToken = null;

        do {
            YouTube.PlaylistItems.List request = youtube.playlistItems()
                    .list(List.of("contentDetails"))
                    .setPlaylistId(uploadsPlaylistId)
                    .setMaxResults(50L)
                    .setKey(apiKey);

            if (pageToken != null) {
                request.setPageToken(pageToken);
            }

            PlaylistItemListResponse response = request.execute();
            if (response.getItems() == null) {
                break;
            }

            boolean reachedOlderVideos = false;
            for (PlaylistItem item : response.getItems()) {
                Instant publishedAt = Instant.ofEpochMilli(
                        item.getContentDetails().getVideoPublishedAt().getValue());
                if (since != null && !publishedAt.isAfter(since)) {
                    reachedOlderVideos = true;
                    break;
                }
                videoIds.add(item.getContentDetails().getVideoId());
            }

            if (reachedOlderVideos) {
                break;
            }

            pageToken = response.getNextPageToken();
        } while (pageToken != null);

        return videoIds;
    }

    private List<YouTubeVideoDto> fetchVideoDetails(YouTube youtube, List<String> videoIds) throws IOException {
        if (videoIds.isEmpty()) {
            return List.of();
        }

        List<YouTubeVideoDto> result = new ArrayList<>();

        for (int i = 0; i < videoIds.size(); i += 50) {
            List<String> batch = videoIds.subList(i, Math.min(i + 50, videoIds.size()));
            VideoListResponse response = youtube.videos()
                    .list(List.of("snippet", "contentDetails", "statistics"))
                    .setId(batch)
                    .setKey(apiKey)
                    .execute();

            if (response.getItems() != null) {
                for (Video video : response.getItems()) {
                    result.add(toDto(video));
                }
            }
        }

        return result;
    }

    private YouTubeVideoDto toDto(Video video) {
        Instant publishedAt = Instant.ofEpochMilli(video.getSnippet().getPublishedAt().getValue());
        Integer durationSeconds = parseDurationSeconds(video.getContentDetails().getDuration());

        Long viewCount = video.getStatistics() != null && video.getStatistics().getViewCount() != null
                ? video.getStatistics().getViewCount().longValue() : null;
        Long likeCount = video.getStatistics() != null && video.getStatistics().getLikeCount() != null
                ? video.getStatistics().getLikeCount().longValue() : null;

        return new YouTubeVideoDto(
                video.getId(),
                video.getSnippet().getTitle(),
                video.getSnippet().getDescription(),
                publishedAt,
                durationSeconds,
                viewCount,
                likeCount
        );
    }

    private Integer parseDurationSeconds(String isoDuration) {
        if (isoDuration == null || isoDuration.isBlank()) {
            return null;
        }
        try {
            return (int) Duration.parse(isoDuration).getSeconds();
        } catch (Exception e) {
            return null;
        }
    }
}
