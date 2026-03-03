package com.tubereturns.service;

import com.tubereturns.model.Video;
import com.tubereturns.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
@Transactional
public class TranscriptDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptDownloadService.class);

    @Value("${tubereturns.yt-dlp.path:yt-dlp}")
    private String ytDlpPath;

    @Value("${tubereturns.yt-dlp.timeout-seconds:300}")
    private int timeoutSeconds;

    @Value("${tubereturns.yt-dlp.enabled:true}")
    private boolean enabled;

    private final VideoRepository videoRepository;

    public TranscriptDownloadService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    public void downloadPendingTranscripts() {
        List<Video> pendingVideos = videoRepository.findByTranscriptStatus(Video.TranscriptStatus.PENDING);
        logger.info("Found {} videos pending transcript download", pendingVideos.size());

        for (Video video : pendingVideos) {
            try {
                downloadTranscript(video);
            } catch (Exception e) {
                logger.error("Error downloading transcript for video {}: {}", video.getVideoId(), e.getMessage(), e);
                video.setTranscriptStatus(Video.TranscriptStatus.FAILED);
                videoRepository.save(video);
            }
        }
    }

    public boolean downloadTranscript(Video video) {
        if (!enabled) {
            logger.warn("yt-dlp is disabled. Skipping transcript for video: {}", video.getVideoId());
            video.setTranscriptStatus(Video.TranscriptStatus.NO_TRANSCRIPT);
            videoRepository.save(video);
            return false;
        }

        logger.info("Downloading transcript for video: {} ({})", video.getTitle(), video.getVideoId());

        try {
            String transcript = executeYtDlp(video.getVideoId());

            if (transcript != null && !transcript.isBlank()) {
                video.setTranscriptText(transcript);
                video.setTranscriptStatus(Video.TranscriptStatus.DOWNLOADED);
                logger.info("Successfully downloaded transcript for video: {}", video.getVideoId());
            } else {
                video.setTranscriptStatus(Video.TranscriptStatus.NO_TRANSCRIPT);
                logger.warn("No transcript available for video: {}", video.getVideoId());
            }

            videoRepository.save(video);
            return transcript != null && !transcript.isBlank();

        } catch (Exception e) {
            logger.error("Failed to download transcript for video {}: {}", video.getVideoId(), e.getMessage());
            video.setTranscriptStatus(Video.TranscriptStatus.FAILED);
            videoRepository.save(video);
            return false;
        }
    }

    private String executeYtDlp(String videoId) throws IOException, InterruptedException {
        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
        Path tempDir = Files.createTempDirectory("tubereturns-transcript-");

        try {
            ProcessBuilder pb = new ProcessBuilder(
                ytDlpPath,
                "--write-subs",       // prefer human-generated captions
                "--write-auto-subs",  // fall back to auto-generated
                "--sub-lang", "en",
                "--sub-format", "vtt",
                "--skip-download",
                "-o", tempDir.resolve("%(id)s.%(ext)s").toString(),
                videoUrl
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            drain(process.getInputStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("yt-dlp timed out after " + timeoutSeconds + "s for video " + videoId);
            }

            Optional<Path> vttFile;
            try (Stream<Path> files = Files.list(tempDir)) {
                vttFile = files.filter(f -> f.toString().endsWith(".vtt")).findFirst();
            }

            if (vttFile.isEmpty()) {
                logger.debug("No .vtt file produced for video {}", videoId);
                return null;
            }

            String vttContent = Files.readString(vttFile.get());
            String plainText = cleanVtt(vttContent);
            logger.info("=== TRANSCRIPT [{}] ===\n{}\n=== END TRANSCRIPT ===", videoId, plainText);
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
    private String cleanVtt(String vttContent) {
        if (vttContent == null || vttContent.isBlank()) {
            return null;
        }

        String[] rawLines = vttContent.split("\r?\n");
        List<String> result = new ArrayList<>();
        String prev = null;

        for (String rawLine : rawLines) {
            String line = rawLine.stripTrailing();

            if (line.startsWith("WEBVTT")) continue;
            if (line.startsWith("Kind:")) continue;
            if (line.startsWith("Language:")) continue;
            if (line.matches("\\d+")) continue;
            if (line.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3} -->.*")) continue;

            // strip HTML / timing tags like <00:00:01.000> or <c.colorname>
            line = line.replaceAll("<[^>]+>", "");
            line = line.stripTrailing();

            if (line.isEmpty()) continue;

            // deduplicate consecutive identical lines (common in auto-captions)
            if (!line.equals(prev)) {
                result.add(line);
                prev = line;
            }
        }

        String text = String.join(" ", result).strip();
        return text.isEmpty() ? null : text;
    }

    private void drain(InputStream stream) {
        try (stream) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException ignored) {
        }
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
