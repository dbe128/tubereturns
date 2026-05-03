package com.tubereturns.service;

import com.tubereturns.model.Video;
import com.tubereturns.repository.VideoRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class TranscriptDownloadService {

    private final PlatformTransactionManager txManager;
    private final VideoRepository videoRepository;

    @Value("${tubereturns.transcript.ytbsd-path:/app/scripts/ytbsd.py}")
    private String ytbsdPath;

    @Value("${tubereturns.transcript.transcripts-dir:/app/transcripts}")
    private String transcriptsDirPath;

    @Value("${tubereturns.transcript.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${tubereturns.transcript.enabled:true}")
    private boolean enabled;

    private final ExecutorService ytbsdExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ytbsd-worker");
        t.setDaemon(true);
        return t;
    });

    private Instant lastProxyRefreshAt = null;

    @PreDestroy
    public void shutdown() {
        ytbsdExecutor.shutdown();
    }

    public int downloadPendingTranscripts(int maxItems) {
        List<Video> pendingVideos = videoRepository.findByTranscriptStatus(Video.TranscriptStatus.PENDING, maxItems);
        log.info("Found {} videos pending transcript download", pendingVideos.size());

        for (Video video : pendingVideos) {
            try {
                boolean ok = downloadTranscript(video);
                log.info("Transcript download {}: {}", ok ? "succeeded" : "yielded no transcript", "https://youtu.be/" + video.getVideoId());
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
            tx.executeWithoutResult(_ -> {
                video.setTranscriptStatus(Video.TranscriptStatus.NO_TRANSCRIPT);
                videoRepository.save(video);
            });
            return false;
        }

        log.info("Downloading transcript for video: {} ({})", video.getTitle(), "https://youtu.be/" + video.getVideoId());

        tx.executeWithoutResult(_ -> {
            video.setTranscriptStatus(Video.TranscriptStatus.DOWNLOADING);
            videoRepository.save(video);
        });

        try {
            String transcript = fetchTranscript(video.getVideoId());

            tx.executeWithoutResult(_ -> {
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
            tx.executeWithoutResult(_ -> {
                video.setTranscriptStatus(Video.TranscriptStatus.FAILED);
                videoRepository.save(video);
            });
            return false;
        }
    }

    String fetchTranscript(String videoId) throws IOException, InterruptedException {
        log.info("Queuing ytbsd job for video: https://youtu.be/{}", videoId);
        try {
            return ytbsdExecutor.submit(() -> runYtbsd(videoId)).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioe) throw ioe;
            if (cause instanceof InterruptedException ie) throw ie;
            throw new RuntimeException(cause.getMessage(), cause);
        }
    }

    private String runYtbsd(String videoId) throws IOException, InterruptedException {
        boolean skipProxyRefresh = lastProxyRefreshAt != null &&
                Duration.between(lastProxyRefreshAt, Instant.now()).toMinutes() < 10;

        if (skipProxyRefresh) {
            log.info("Skipping proxy refresh — last refresh was {} min ago", Duration.between(lastProxyRefreshAt, Instant.now()).toMinutes());
        } else {
            log.info("Refreshing proxies (last refresh: {})", lastProxyRefreshAt != null ? lastProxyRefreshAt : "never");
            lastProxyRefreshAt = Instant.now();
        }

        List<String> cmd = new ArrayList<>(List.of("python3", ytbsdPath, "--mode", "batch", "--urls", videoId));
        if (skipProxyRefresh) {
            cmd.add("--no-proxy-refresh");
        }

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

        log.info("ytbsd.py output for {}:\n{}", "https://youtu.be/" + videoId, output);

        Path transcriptsDir = Path.of(transcriptsDirPath).toAbsolutePath();
        Path mdFile = findOutputFile(transcriptsDir, videoId);

        if (mdFile == null) {
            log.warn("No output file found for video {} under {}", "https://youtu.be/" + videoId, transcriptsDir);
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

    private Path findOutputFile(Path transcriptsDir, String videoId) throws IOException {
        if (!Files.isDirectory(transcriptsDir)) {
            return null;
        }
        try (Stream<Path> files = Files.walk(transcriptsDir, 2)) {
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
