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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class TranscriptDownloadService {

    private final PlatformTransactionManager txManager;
    private final VideoRepository videoRepository;

    @Value("${tubereturns.transcript.ytbsd-path:/app/ytbsd.py}")
    private String ytbsdPath;

    @Value("${tubereturns.transcript.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${tubereturns.transcript.enabled:true}")
    private boolean enabled;

    public int downloadPendingTranscripts(int maxItems) {
        List<Video> pendingVideos = videoRepository.findByTranscriptStatus(Video.TranscriptStatus.PENDING, maxItems);
        log.info("Found {} videos pending transcript download", pendingVideos.size());

        for (Video video : pendingVideos) {
            try {
                downloadTranscript(video);
            } catch (Exception e) {
                log.error("Error downloading transcript for video {}: {}", "https://youtu.be/" + video.getVideoId(), e.getMessage(), e);
            }
        }
        return pendingVideos.size();
    }

    public boolean downloadTranscript(Video video) {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        if (!enabled) {
            log.warn("Transcript download is disabled. Skipping video: {}", "https://youtu.be/" + video.getVideoId());
            tx.executeWithoutResult(s -> {
                video.setTranscriptStatus(Video.TranscriptStatus.NO_TRANSCRIPT);
                videoRepository.save(video);
            });
            return false;
        }

        log.info("Downloading transcript for video: {} ({})", video.getTitle(), "https://youtu.be/" + video.getVideoId());

        tx.executeWithoutResult(s -> {
            video.setTranscriptStatus(Video.TranscriptStatus.DOWNLOADING);
            videoRepository.save(video);
        });

        try {
            String transcript = fetchTranscript(video.getVideoId());

            tx.executeWithoutResult(s -> {
                if (transcript != null && !transcript.isBlank()) {
                    video.setTranscriptText(transcript);
                    video.setTranscriptStatus(Video.TranscriptStatus.DOWNLOADED);
                    log.info("Successfully downloaded transcript for video: {}", "https://youtu.be/" + video.getVideoId());
                } else {
                    video.setTranscriptStatus(Video.TranscriptStatus.NO_TRANSCRIPT);
                    log.warn("No transcript available for video: {}", "https://youtu.be/" + video.getVideoId());
                }
                videoRepository.save(video);
            });
            return transcript != null && !transcript.isBlank();

        } catch (Exception e) {
            log.error("Failed to download transcript for video {}: {}", "https://youtu.be/" + video.getVideoId(), e.getMessage());
            tx.executeWithoutResult(s -> {
                video.setTranscriptStatus(Video.TranscriptStatus.FAILED);
                videoRepository.save(video);
            });
            return false;
        }
    }

    String fetchTranscript(String videoId) throws IOException, InterruptedException {
        List<String> cmd = List.of("python3", ytbsdPath, "--mode", "single", "--url", videoId, "--no-proxy-refresh");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        log.info("Running ytbsd.py: {}", cmd);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("ytbsd.py timed out after " + timeoutSeconds + "s for video " + videoId);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.warn("ytbsd.py exited with code {} for video {}:\n{}", exitCode, "https://youtu.be/" + videoId, output);
            throw new RuntimeException("ytbsd.py failed for video " + videoId + ": " + output.trim());
        }

        log.debug("ytbsd.py output for {}:\n{}", "https://youtu.be/" + videoId, output);

        Path subtitlesDir = Path.of(ytbsdPath).toAbsolutePath().getParent().resolve("subtitles");
        Path mdFile = findOutputFile(subtitlesDir, videoId);

        if (mdFile == null) {
            log.warn("No output file found for video {} under {}", "https://youtu.be/" + videoId, subtitlesDir);
            return null;
        }

        try {
            String markdown = Files.readString(mdFile);
            String transcript = parseMarkdownTranscript(markdown);
            if (transcript != null) {
                log.info("Transcript [{}] ({} chars): {}", "https://youtu.be/" + videoId, transcript.length(),
                        transcript.length() > 200 ? transcript.substring(0, 200) + "…" : transcript);
            }
            return transcript;
        } finally {
            deleteOutputFile(mdFile);
        }
    }

    private Path findOutputFile(Path subtitlesDir, String videoId) throws IOException {
        if (!Files.isDirectory(subtitlesDir)) {
            return null;
        }
        try (Stream<Path> files = Files.walk(subtitlesDir, 2)) {
            return files
                    .filter(p -> p.getFileName().toString().startsWith(videoId))
                    .filter(p -> p.toString().endsWith(".md"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private String parseMarkdownTranscript(String markdown) {
        int start = markdown.indexOf("### Transcript");
        if (start == -1) {
            return null;
        }
        start = markdown.indexOf("\n\n", start);
        if (start == -1) {
            return null;
        }
        start += 2;
        int end = markdown.indexOf("\n\n---", start);
        if (end == -1) {
            end = markdown.length();
        }
        String text = markdown.substring(start, end).strip();
        return text.isBlank() ? null : text;
    }

    private void deleteOutputFile(Path file) {
        try {
            Files.deleteIfExists(file);
            Path parent = file.getParent();
            if (parent != null) {
                try (Stream<Path> entries = Files.list(parent)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }
}
