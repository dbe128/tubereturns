package com.tubereturns.service;

import com.tubereturns.model.Video;
import com.tubereturns.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class TranscriptDownloadService {

    private final PlatformTransactionManager txManager;

    @Value("${tubereturns.yt-dlp.path:yt-dlp}")
    private String ytDlpPath;

    @Value("${tubereturns.yt-dlp.timeout-seconds:300}")
    private int timeoutSeconds;

    @Value("${tubereturns.yt-dlp.enabled:true}")
    private boolean enabled;

    @Value("${tubereturns.yt-dlp.cookies-path:}")
    private String cookiesPath;

    private final VideoRepository videoRepository;

    public int downloadPendingTranscripts(int maxItems) {
        List<Video> pendingVideos = videoRepository.findByTranscriptStatus(Video.TranscriptStatus.PENDING, maxItems);
        log.info("Found {} videos pending transcript download", pendingVideos.size());

        TransactionTemplate tx = new TransactionTemplate(txManager);
        for (Video video : pendingVideos) {
            try {
                tx.executeWithoutResult(status -> downloadTranscript(video));
            } catch (Exception e) {
                log.error("Error downloading transcript for video {}: {}", "https://youtu.be/" + video.getVideoId(), e.getMessage(), e);
            }
        }
        return pendingVideos.size();
    }

    public boolean downloadTranscript(Video video) {
        if (!enabled) {
            log.warn("yt-dlp is disabled. Skipping transcript for video: {}", "https://youtu.be/" + video.getVideoId());
            video.setTranscriptStatus(Video.TranscriptStatus.NO_TRANSCRIPT);
            videoRepository.save(video);
            return false;
        }

        log.info("Downloading transcript for video: {} ({})", video.getTitle(), "https://youtu.be/" + video.getVideoId());

        try {
            String transcript = executeYtDlp(video.getVideoId());

            if (transcript != null && !transcript.isBlank()) {
                video.setTranscriptText(transcript);
                video.setTranscriptStatus(Video.TranscriptStatus.DOWNLOADED);
                log.info("Successfully downloaded transcript for video: {}", "https://youtu.be/" + video.getVideoId());
            } else {
                video.setTranscriptStatus(Video.TranscriptStatus.NO_TRANSCRIPT);
                log.warn("No transcript available for video: {}", "https://youtu.be/" + video.getVideoId());
            }

            videoRepository.save(video);
            return transcript != null && !transcript.isBlank();

        } catch (Exception e) {
            log.error("Failed to download transcript for video {}: {}", "https://youtu.be/" + video.getVideoId(), e.getMessage());
            video.setTranscriptStatus(Video.TranscriptStatus.FAILED);
            videoRepository.save(video);
            return false;
        }
    }

    String executeYtDlp(String videoId) throws IOException, InterruptedException {
        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
        Path tempDir = Files.createTempDirectory("tubereturns-transcript-");

        try {
            int sleepSeconds = 10 + new Random().nextInt(6);
            List<String> cmd = new ArrayList<>(List.of(
                ytDlpPath,
                "--write-subs",
                "--write-auto-subs",
                "--sub-lang", "en",
                "--sub-format", "vtt",
                "--skip-download",
                "--remote-components", "ejs:github",
                "--sleep-subtitles", String.valueOf(sleepSeconds),
                "-o", tempDir.resolve("%(id)s.%(ext)s").toString()
            ));
            if (cookiesPath != null && !cookiesPath.isBlank()) {
                Path resolvedCookies = Path.of(cookiesPath).toAbsolutePath();
                log.info("Cookies file: {} (exists: {})", resolvedCookies, Files.exists(resolvedCookies));
                cmd.add("--cookies");
                cmd.add(resolvedCookies.toString());
            }
            cmd.add(videoUrl);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            log.info("Running yt-dlp: {}", cmd);
            Process process = pb.start();
            String ytDlpOutput = new String(process.getInputStream().readAllBytes());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("yt-dlp timed out after " + timeoutSeconds + "s for video " + videoId);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("yt-dlp exited with code {} for video {}:\n{}", exitCode, "https://youtu.be/" + videoId, ytDlpOutput);
            } else {
                log.debug("yt-dlp output for {}:\n{}", "https://youtu.be/" + videoId, ytDlpOutput);
            }

            Optional<Path> vttFile;
            try (Stream<Path> files = Files.list(tempDir)) {
                vttFile = files
                        .filter(f -> f.toString().endsWith(".vtt"))
                        .min(Comparator.comparingInt(p -> p.toString().contains(".auto.") ? 1 : 0));
            }

            if (vttFile.isEmpty()) {
                log.warn("No .vtt file produced for video {}", "https://youtu.be/" + videoId);
                return null;
            }

            String vttContent = Files.readString(vttFile.get());
            String plainText = cleanVtt(vttContent);
            log.info("Transcript [{}] ({} chars): {}", "https://youtu.be/" + videoId, plainText.length(),
                    plainText.length() > 200 ? plainText.substring(0, 200) + "…" : plainText);
            return plainText;

        } finally {
            deleteDir(tempDir);
        }
    }

    /**
     * Cleans a VTT subtitle file to plain text.
     * Equivalent to:
     *   sed -E '/^WEBVTT/d; /^Kind:/d; /^Language:/d; /^[0-9]+$/d;
     *           /^[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3} -->/d;
     *           s/<[^>]+>//g; s/[[:space:]]+$//; /^$/d'
     *   | awk '!(NR>1 && $0==prev){print} {prev=$0}'
     */
    static String cleanVtt(String vttContent) {
        if (vttContent == null || vttContent.isBlank()) {
            return null;
        }

        String[] rawLines = vttContent.split("\r?\n");
        List<String> result = new ArrayList<>();
        String prev = null;

        for (String rawLine : rawLines) {
            String line = rawLine.stripTrailing();

            if (line.startsWith("WEBVTT")) {
                continue;
            }
            if (line.startsWith("Kind:")) {
                continue;
            }
            if (line.startsWith("Language:")) {
                continue;
            }
            if (line.matches("\\d+")) {
                continue;
            }
            if (line.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3} -->.*")) {
                continue;
            }

            // strip HTML / timing tags like <00:00:01.000> or <c.colorname>
            line = line.replaceAll("<[^>]+>", "");
            line = line.stripTrailing();

            if (line.isEmpty()) {
                continue;
            }

            // deduplicate consecutive identical lines (common in auto-captions)
            if (!line.equals(prev)) {
                result.add(line);
                prev = line;
            }
        }

        String text = String.join(" ", result).strip();
        return text.isEmpty() ? null : text;
    }

    private void deleteDir(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
