package com.tubereturns.service;

import com.microsoft.playwright.*;
import com.tubereturns.model.Channel;
import com.tubereturns.repository.ArchivarixDeletedVideoRepository;
import com.tubereturns.repository.ChannelRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class ArchivarixService {

    private static final String BASE_URL = "https://tube.archivarix.net/?q=";
    private static final Pattern SHOWING = Pattern.compile("Showing (\\d+) of (\\d+)");
    private static final Pattern BADGE_COUNT = Pattern.compile("(\\d+)");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    @Value("${tubereturns.archivarix.enabled}")
    private boolean enabled;

    private final ChannelRepository channelRepository;
    private final ArchivarixDeletedVideoRepository deletedVideoRepository;
    private final CacheManager cacheManager;

    private volatile Playwright playwright;
    private volatile Browser browser;

    @PostConstruct
    void init() {
        if (!enabled) {
            log.info("Archivarix sync disabled — skipping browser initialization");
            return;
        }
        Thread.ofVirtual().name("archivarix-init").start(() -> {
            try {
                log.info("Initializing Playwright browser for Archivarix sync");
                Playwright pw = Playwright.create();
                Browser b = pw.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(List.of("--disable-blink-features=AutomationControlled", "--no-sandbox")));
                playwright = pw;
                browser = b;
                log.info("Playwright Chromium browser initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize Playwright browser: {}", e.getMessage(), e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        log.info("Shutting down Archivarix Playwright browser");
        if (browser != null) {
            browser.close();
            log.info("Playwright browser closed");
        }
        if (playwright != null) {
            playwright.close();
            log.info("Playwright instance closed");
        }
    }

    public int syncNextBatch(int maxItems) {
        if (!enabled) {
            log.debug("Archivarix sync disabled — skipping");
            return 0;
        }
        if (browser == null) {
            log.info("Archivarix sync: browser not yet initialized, skipping");
            return 0;
        }
        List<Channel> channels = channelRepository.findUnsyncedArchivarixChannels(maxItems);
        if (channels.isEmpty()) {
            log.info("Archivarix sync: no unsynced channels found — all channels already have archive data");
            return 0;
        }
        log.info("Archivarix sync: found {} channel(s) to sync (max {})", channels.size(), maxItems);
        int synced = 0;
        for (Channel channel : channels) {
            try {
                syncChannel(channel);
                synced++;
            } catch (Exception e) {
                log.error("Archivarix sync failed for channel '{}' ({}): {}", channel.getChannelName(), channel.getHandle(), e.getMessage(), e);
            }
        }
        log.info("Archivarix sync complete: {}/{} channel(s) synced successfully", synced, channels.size());
        return synced;
    }

    private void syncChannel(Channel channel) {
        String channelId = channel.getYoutubeChannelId();
        if (channelId == null || channelId.isBlank()) {
            log.warn("Archivarix sync: channel '{}' has no YouTube channel ID yet, skipping",
                    channel.getChannelName());
            return;
        }

        log.info("Archivarix sync: starting scrape for '{}' (channelId={})", channel.getChannelName(), channelId);

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"));
        try {
            Page page = context.newPage();
            page.navigate(BASE_URL + channelId);

            log.info("Archivarix sync: waiting 5 minutes for search to complete for '{}'", channel.getChannelName());
            for (int i = 0; i < 10; i++) {
                page.waitForTimeout(30_000);
                log.info("Archivarix sync: still waiting for '{}' ({}/10)", channel.getChannelName(), i + 1);
            }

            Locator deletedTab = page.locator("button[role='tab']").filter(new Locator.FilterOptions().setHasText("Deleted"));
            if (deletedTab.count() == 0) {
                log.info("Archivarix sync: no Deleted tab found for '{}' — recording 0 deleted videos", channel.getChannelName());
                channel.setArchivarixDeletedCount(0);
                channel.setArchivarixCheckedAt(Instant.now());
                channelRepository.save(channel);
                evictAllCaches();
                return;
            }

            String tabText = deletedTab.first().textContent();
            Matcher badgeMatcher = BADGE_COUNT.matcher(tabText);
            int totalDeleted = badgeMatcher.find() ? Integer.parseInt(badgeMatcher.group(1)) : 0;
            log.info("Archivarix sync: '{}' — tab badge shows {} total deleted video(s)", channel.getChannelName(), totalDeleted);

            deletedTab.first().click();
            log.debug("Archivarix sync: clicked Deleted tab for '{}'", channel.getChannelName());

            Locator statusLocator = page.locator("p.tabular-nums").first();
            statusLocator.waitFor(new Locator.WaitForOptions().setTimeout(30_000));

            boolean seenResults = false;
            String lastStatus = "";
            int stableSeconds = 0;
            long deadline = System.currentTimeMillis() + 300_000;
            long streamLastLog = System.currentTimeMillis();
            while (System.currentTimeMillis() < deadline) {
                String statusText = statusLocator.textContent();
                Matcher m = SHOWING.matcher(statusText);
                if (m.find()) {
                    seenResults = true;
                    if (m.group(1).equals(m.group(2)) && !m.group(1).equals("0")) {
                        log.debug("Archivarix sync: stream complete for '{}' — {}", channel.getChannelName(), statusText);
                        break;
                    }
                }
                if (seenResults) {
                    stableSeconds = statusText.equals(lastStatus) ? stableSeconds + 1 : 0;
                    if (stableSeconds >= 5) {
                        log.debug("Archivarix sync: stream stable for 5s for '{}' — {}", channel.getChannelName(), statusText);
                        break;
                    }
                }
                if (System.currentTimeMillis() - streamLastLog >= 30_000) {
                    log.info("Archivarix sync: still waiting for stream for '{}' — {}", channel.getChannelName(), statusText);
                    streamLastLog = System.currentTimeMillis();
                }
                lastStatus = statusText;
                page.waitForTimeout(1_000);
            }

            List<DeletedVideo> videos = extractDeletedVideos(page, channel.getChannelName());
            log.info("Archivarix sync: '{}' — {} visible deleted video(s) extracted (total including paywall: {})",
                    channel.getChannelName(), videos.size(), totalDeleted);

            int withTitle = 0;
            for (DeletedVideo video : videos) {
                String uploadDateStr = video.uploadDate() != null ? video.uploadDate().toString() : null;
                deletedVideoRepository.upsert(channel.getId(), video.videoId(), video.title(), uploadDateStr, video.status());
                if (video.title() != null) { withTitle++; }
                log.debug("Archivarix sync: upserted video [{}] '{}' ({}) for '{}'",
                        video.videoId(), video.title(), video.status(), channel.getChannelName());
            }
            log.info("Archivarix sync: '{}' — upserted {} video record(s) ({} with titles, {} ID-only)",
                    channel.getChannelName(), videos.size(), withTitle, videos.size() - withTitle);

            channel.setArchivarixDeletedCount(totalDeleted);
            channel.setArchivarixCheckedAt(Instant.now());
            channelRepository.save(channel);
            evictAllCaches();
            log.info("Archivarix sync: '{}' complete — deleted_count={}, checked_at={}",
                    channel.getChannelName(), totalDeleted, channel.getArchivarixCheckedAt());
        } finally {
            context.close();
        }
    }

    private void evictAllCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        log.debug("Archivarix sync: all caches evicted");
    }

    private List<DeletedVideo> extractDeletedVideos(Page page, String channelName) {
        List<DeletedVideo> results = new ArrayList<>();
        Locator allBadges = page.locator("a.bg-red-100");
        int badgeCount = allBadges.count();
        log.debug("Archivarix sync: found {} deleted badge(s) on page for '{}'", badgeCount, channelName);

        for (int i = 0; i < badgeCount; i++) {
            try {
                Locator badge = allBadges.nth(i);
                Locator cardRoot = badge.locator("xpath=../../..");
                String title = cardRoot.locator("span.truncate").first().textContent().trim();
                String videoId = cardRoot.locator("span.font-mono").first().textContent().trim();
                String status = badge.textContent().trim();
                String uploadDateRaw = cardRoot.locator("div.flex-wrap span").first().textContent().trim();
                LocalDate uploadDate = parseDate(uploadDateRaw);

                results.add(new DeletedVideo(
                        videoId.isEmpty() ? null : videoId,
                        title.isEmpty() ? null : title,
                        status,
                        uploadDate));
            } catch (Exception e) {
                log.warn("Archivarix sync: failed to extract video card {} for '{}': {}", i, channelName, e.getMessage());
            }
        }
        return results;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            log.debug("Archivarix sync: could not parse date '{}': {}", raw, e.getMessage());
            return null;
        }
    }

    private record DeletedVideo(String videoId, String title, String status, LocalDate uploadDate) {}
}
