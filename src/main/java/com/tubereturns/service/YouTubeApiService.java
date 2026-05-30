package com.tubereturns.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.tubereturns.dto.ChannelSearchResultDto;
import com.tubereturns.dto.YouTubeVideoDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeApiService {

    private final MeterRegistry meterRegistry;

    @Value("${tubereturns.youtube.api-key}")
    private String apiKey;

    @Value("${tubereturns.youtube.enabled}")
    private boolean enabled;

    @Value("${tubereturns.youtube.channel-filter-keywords}")
    private List<String> channelFilterKeywords;

    public record ChannelInfo(String uploadsPlaylistId, String thumbnailUrl, Long subscriberCount) {}

    @PostConstruct
    private void initMetrics() {
        for (String method : List.of("searchChannels", "getRecentVideoTitles", "resolveChannelInfo", "getVideosFromPlaylist", "resolveChannelByHandle")) {
            Counter.builder("tubereturns.youtube.api.calls").tag("method", method).register(meterRegistry);
            Counter.builder("tubereturns.youtube.api.errors").tag("method", method).register(meterRegistry);
        }
        Counter.builder("tubereturns.youtube.quota.units").register(meterRegistry);
    }

    private void quota(long units) {
        meterRegistry.counter("tubereturns.youtube.quota.units").increment(units);
    }

    public List<ChannelSearchResultDto> searchChannels(String query, boolean filterByKeywords) {
        meterRegistry.counter("tubereturns.youtube.api.calls", "method", "searchChannels").increment();
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        try {
            YouTube youtube = buildClient();

            List<SearchResult> searchItems = youtube.search()
                    .list(List.of("snippet"))
                    .setQ(query)
                    .setType(List.of("channel"))
                    .setMaxResults(50L)
                    .setKey(apiKey)
                    .execute()
                    .getItems();
            quota(100);

            if (searchItems == null || searchItems.isEmpty()) {
                return List.of();
            }

            List<String> channelIds = searchItems.stream()
                    .map(item -> item.getId().getChannelId())
                    .toList();

            Map<String, com.google.api.services.youtube.model.Channel> detailMap = new HashMap<>();
            var detailResponse = youtube.channels()
                    .list(List.of("snippet", "brandingSettings", "statistics"))
                    .setId(channelIds)
                    .setKey(apiKey)
                    .execute();
            quota(1);
            if (detailResponse.getItems() != null) {
                for (var ch : detailResponse.getItems()) {
                    detailMap.put(ch.getId(), ch);
                }
            }

            List<ChannelSearchResultDto> results = new ArrayList<>();
            for (SearchResult item : searchItems) {
                String channelId = item.getId().getChannelId();
                var detail = detailMap.get(channelId);

                String thumbnailUrl = null;
                if (item.getSnippet().getThumbnails() != null) {
                    var t = item.getSnippet().getThumbnails();
                    if (t.getHigh() != null) { thumbnailUrl = t.getHigh().getUrl(); }
                    else if (t.getMedium() != null) { thumbnailUrl = t.getMedium().getUrl(); }
                    else if (t.getDefault() != null) { thumbnailUrl = t.getDefault().getUrl(); }
                }

                if (detail == null || detail.getSnippet() == null) continue;
                String customUrl = detail.getSnippet().getCustomUrl();
                if (customUrl == null || customUrl.isBlank()) continue;
                String handle = customUrl.toLowerCase().replaceAll("^@", "").replaceAll("/+$", "");
                String channelUrl = "https://www.youtube.com/@" + handle;
                String description = detail.getSnippet().getDescription();

                String channelTags = detail.getBrandingSettings() != null
                        && detail.getBrandingSettings().getChannel() != null
                        ? detail.getBrandingSettings().getChannel().getKeywords()
                        : null;

                if (filterByKeywords && !matchesFinanceKeywords(channelTags, description)) continue;

                Long subscriberCount = null;
                Long videoCount = null;
                if (detail.getStatistics() != null) {
                    if (!Boolean.TRUE.equals(detail.getStatistics().getHiddenSubscriberCount())
                            && detail.getStatistics().getSubscriberCount() != null) {
                        subscriberCount = detail.getStatistics().getSubscriberCount().longValue();
                    }
                    if (detail.getStatistics().getVideoCount() != null) {
                        videoCount = detail.getStatistics().getVideoCount().longValue();
                    }
                }

                String channelCreatedAt = null;
                if (detail.getSnippet().getPublishedAt() != null) {
                    channelCreatedAt = Instant.ofEpochMilli(detail.getSnippet().getPublishedAt().getValue())
                            .toString().substring(0, 10);
                }

                results.add(new ChannelSearchResultDto(
                        handle, item.getSnippet().getTitle(), channelUrl, thumbnailUrl, description,
                        subscriberCount, videoCount, channelCreatedAt, channelId));
            }
            return results;
        } catch (Exception e) {
            meterRegistry.counter("tubereturns.youtube.api.errors", "method", "searchChannels").increment();
            log.error("Failed to search channels for '{}': {}", query, e.getMessage(), e);
            return List.of();
        }
    }

    public List<String> getRecentVideoTitles(String handle, int maxResults) {
        meterRegistry.counter("tubereturns.youtube.api.calls", "method", "getRecentVideoTitles").increment();
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        try {
            YouTube youtube = buildClient();
            ChannelInfo info = resolveUploadsPlaylistId(youtube, "https://www.youtube.com/@" + handle);
            if (info == null || info.uploadsPlaylistId() == null) {
                return List.of();
            }
            PlaylistItemListResponse response = youtube.playlistItems()
                    .list(List.of("snippet"))
                    .setPlaylistId(info.uploadsPlaylistId())
                    .setMaxResults((long) Math.min(maxResults, 50))
                    .setKey(apiKey)
                    .execute();
            quota(1);
            if (response.getItems() == null) {
                return List.of();
            }
            return response.getItems().stream()
                    .map(item -> item.getSnippet().getTitle())
                    .filter(t -> t != null && !t.isBlank())
                    .toList();
        } catch (Exception e) {
            meterRegistry.counter("tubereturns.youtube.api.errors", "method", "getRecentVideoTitles").increment();
            log.error("Failed to fetch video titles for @{}: {}", handle, e.getMessage(), e);
            return List.of();
        }
    }

    public ChannelInfo resolveChannelInfo(String channelUrl) {
        meterRegistry.counter("tubereturns.youtube.api.calls", "method", "resolveChannelInfo").increment();
        if (!enabled || channelUrl == null || channelUrl.isBlank()) {
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            return resolveUploadsPlaylistId(buildClient(), channelUrl);
        } catch (Exception e) {
            meterRegistry.counter("tubereturns.youtube.api.errors", "method", "resolveChannelInfo").increment();
            log.error("Failed to resolve channel info for {}: {}", channelUrl, e.getMessage(), e);
            return null;
        }
    }

    public ChannelSearchResultDto resolveChannelByHandle(String handle) {
        meterRegistry.counter("tubereturns.youtube.api.calls", "method", "resolveChannelByHandle").increment();
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            YouTube youtube = buildClient();
            var response = youtube.channels()
                    .list(List.of("snippet", "statistics"))
                    .set("forHandle", handle)
                    .setKey(apiKey)
                    .execute();
            quota(1);
            if (response.getItems() == null || response.getItems().isEmpty()) {
                return null;
            }
            var item = response.getItems().getFirst();
            String customUrl = item.getSnippet().getCustomUrl();
            String resolvedHandle = customUrl != null
                    ? customUrl.toLowerCase().replaceAll("^@", "")
                    : handle.toLowerCase();
            String channelUrl = "https://www.youtube.com/@" + resolvedHandle;
            String thumbnailUrl = null;
            if (item.getSnippet().getThumbnails() != null) {
                var t = item.getSnippet().getThumbnails();
                if (t.getHigh() != null) { thumbnailUrl = t.getHigh().getUrl(); }
                else if (t.getMedium() != null) { thumbnailUrl = t.getMedium().getUrl(); }
                else if (t.getDefault() != null) { thumbnailUrl = t.getDefault().getUrl(); }
            }
            Long subscriberCount = null;
            Long videoCount = null;
            if (item.getStatistics() != null) {
                if (!Boolean.TRUE.equals(item.getStatistics().getHiddenSubscriberCount())
                        && item.getStatistics().getSubscriberCount() != null) {
                    subscriberCount = item.getStatistics().getSubscriberCount().longValue();
                }
                if (item.getStatistics().getVideoCount() != null) {
                    videoCount = item.getStatistics().getVideoCount().longValue();
                }
            }
            String channelCreatedAt = null;
            if (item.getSnippet().getPublishedAt() != null) {
                channelCreatedAt = Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue())
                        .toString().substring(0, 10);
            }
            return new ChannelSearchResultDto(resolvedHandle, item.getSnippet().getTitle(), channelUrl,
                    thumbnailUrl, item.getSnippet().getDescription(), subscriberCount, videoCount, channelCreatedAt, item.getId());
        } catch (Exception e) {
            meterRegistry.counter("tubereturns.youtube.api.errors", "method", "resolveChannelByHandle").increment();
            log.error("Failed to resolve channel for handle @{}: {}", handle, e.getMessage(), e);
            return null;
        }
    }

    public List<YouTubeVideoDto> getVideosFromPlaylist(String uploadsPlaylistId, Instant since) {
        meterRegistry.counter("tubereturns.youtube.api.calls", "method", "getVideosFromPlaylist").increment();
        if (!enabled || uploadsPlaylistId == null || uploadsPlaylistId.isBlank()) {
            return List.of();
        }
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        try {
            YouTube youtube = buildClient();
            List<String> videoIds = fetchVideoIds(youtube, uploadsPlaylistId, since);
            return fetchVideoDetails(youtube, videoIds).stream()
                    .sorted(Comparator.comparing(YouTubeVideoDto::publishedAt))
                    .toList();
        } catch (Exception e) {
            meterRegistry.counter("tubereturns.youtube.api.errors", "method", "getVideosFromPlaylist").increment();
            log.error("Failed to fetch videos from playlist {}: {}", uploadsPlaylistId, e.getMessage(), e);
            return List.of();
        }
    }

    private boolean matchesFinanceKeywords(String channelTags, String description) {
        if (channelFilterKeywords == null || channelFilterKeywords.isEmpty()) {
            return true;
        }
        String haystack = (channelTags != null ? channelTags : "") + " " + (description != null ? description : "");
        String lower = haystack.toLowerCase();
        return channelFilterKeywords.stream().anyMatch(kw -> lower.contains(kw.toLowerCase()));
    }

    private YouTube buildClient() throws GeneralSecurityException, IOException {
        return new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {
                    request.setConnectTimeout(10_000);
                    request.setReadTimeout(60_000);
                }
        ).setApplicationName("tubereturns").build();
    }

    private ChannelInfo resolveUploadsPlaylistId(YouTube youtube, String channelUrl) throws IOException {
        YouTube.Channels.List request = youtube.channels()
                .list(List.of("contentDetails", "snippet", "statistics"))
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
        quota(1);
        if (response.getItems() == null || response.getItems().isEmpty()) {
            return null;
        }

        var item = response.getItems().getFirst();
        String uploadsPlaylistId = item.getContentDetails().getRelatedPlaylists().getUploads();
        String thumbnailUrl = null;
        if (item.getSnippet() != null && item.getSnippet().getThumbnails() != null) {
            var thumbnails = item.getSnippet().getThumbnails();
            if (thumbnails.getHigh() != null) {
                thumbnailUrl = thumbnails.getHigh().getUrl();
            } else if (thumbnails.getMedium() != null) {
                thumbnailUrl = thumbnails.getMedium().getUrl();
            } else if (thumbnails.getDefault() != null) {
                thumbnailUrl = thumbnails.getDefault().getUrl();
            }
        }
        Long subscriberCount = null;
        if (item.getStatistics() != null
                && !Boolean.TRUE.equals(item.getStatistics().getHiddenSubscriberCount())
                && item.getStatistics().getSubscriberCount() != null) {
            subscriberCount = item.getStatistics().getSubscriberCount().longValue();
        }
        return new ChannelInfo(uploadsPlaylistId, thumbnailUrl, subscriberCount);
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
            quota(1);
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
            quota(1);

            if (response.getItems() != null) {
                for (Video video : response.getItems()) {
                    YouTubeVideoDto dto = toDto(video);
                    if (dto.durationSeconds() != null && dto.durationSeconds() <= 60) {
                        continue;
                    }
                    result.add(dto);
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
