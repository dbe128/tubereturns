package com.tubereturns.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tubereturns.dto.ChannelSearchResultDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class ChannelSearchService {

    private static final long SEARCH_QUOTA_UNITS = 101;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 100;

    private final YouTubeApiService youTubeApiService;
    private final YouTubeInteractiveBudget budget;

    @Value("${tubereturns.youtube.search-cache-hours}")
    private long searchCacheHours;

    private Cache<String, List<ChannelSearchResultDto>> cache;

    @PostConstruct
    private void initCache() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(searchCacheHours))
                .maximumSize(2000)
                .build();
    }

    public Optional<List<ChannelSearchResultDto>> search(String query, boolean filterByKeywords, boolean authenticated) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < MIN_QUERY_LENGTH || normalized.length() > MAX_QUERY_LENGTH) {
            return Optional.of(List.of());
        }
        String key = filterByKeywords + "|" + normalized;
        List<ChannelSearchResultDto> cached = cache.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (!budget.tryConsume(authenticated, SEARCH_QUOTA_UNITS)) {
            return Optional.empty();
        }
        List<ChannelSearchResultDto> results = youTubeApiService.searchChannels(normalized, filterByKeywords);
        if (!results.isEmpty()) {
            cache.put(key, results);
        }
        return Optional.of(results);
    }
}
