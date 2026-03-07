package com.tubereturns.service;

import com.tubereturns.dto.YouTubeVideoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class YouTubeApiService {

    private static final DateTimeFormatter UPLOAD_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${tubereturns.yt-dlp.path:yt-dlp}")
    private String ytDlpPath;

    @Value("${tubereturns.yt-dlp.timeout-seconds:300}")
    private int timeoutSeconds;

    @Value("${tubereturns.yt-dlp.enabled:true}")
    private boolean enabled;

    public List<YouTubeVideoDto> getRecentVideos(String channelUrl, Instant since) {
        if (!enabled || channelUrl == null || channelUrl.isBlank()) {
            log.warn("yt-dlp disabled or no channel URL — skipping video discovery");
            return List.of();
        }

        String videosUrl = channelUrl.endsWith("/")
                ? channelUrl + "videos"
                : channelUrl + "/videos";

        log.info("Discovering videos for channel: {}", videosUrl);

        try {
            return fetchVideosViaYtDlp(videosUrl, since);
        } catch (Exception e) {
            log.error("Failed to discover videos for {}: {}", channelUrl, e.getMessage(), e);
            return List.of();
        }
    }

    private List<YouTubeVideoDto> fetchVideosViaYtDlp(String playlistUrl, Instant since)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(
                ytDlpPath,
                "--flat-playlist",
                "--print", "%(id)s\t%(title)s\t%(upload_date)s\t%(duration)s\t%(view_count)s",
                "--playlist-end", "50",
                playlistUrl
        );
        pb.redirectErrorStream(false);

        Process process = pb.start();

        List<YouTubeVideoDto> videos = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                YouTubeVideoDto dto = parseLine(line);
                if (dto != null && (since == null || dto.publishedAt().isAfter(since))) {
                    videos.add(dto);
                }
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("yt-dlp --flat-playlist timed out for {}", playlistUrl);
        }

        log.info("Discovered {} videos from {}", videos.size(), playlistUrl);
        return videos;
    }

    private YouTubeVideoDto parseLine(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 5) {
            log.debug("Skipping malformed yt-dlp output line: {}", line);
            return null;
        }

        String videoId = parts[0].strip();
        String title = parts[1].strip();
        String dateStr = parts[2].strip();
        String durStr = parts[3].strip();
        String viewStr = parts[4].strip();

        if (videoId.isBlank() || title.isBlank()) {
            return null;
        }

        Instant publishedAt = parseUploadDate(dateStr);
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }

        Integer duration = parseIntOrNull(durStr);
        Long viewCount = parseLongOrNull(viewStr);

        return new YouTubeVideoDto(videoId, title, null, publishedAt, duration, viewCount, null);
    }

    private Instant parseUploadDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank() || dateStr.equals("NA")) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr, UPLOAD_DATE_FMT);
            return date.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            log.debug("Could not parse upload_date '{}': {}", dateStr, e.getMessage());
            return null;
        }
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank() || s.equals("NA") || s.equals("None")) {
            return null;
        }
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLongOrNull(String s) {
        if (s == null || s.isBlank() || s.equals("NA") || s.equals("None")) {
            return null;
        }
        try {
            return Long.parseLong(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
