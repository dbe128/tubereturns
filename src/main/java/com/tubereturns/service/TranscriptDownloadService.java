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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class TranscriptDownloadService {

    private final PlatformTransactionManager txManager;
    private final VideoRepository videoRepository;
    private final StockPickExtractionService extractionService;

    @Value("${tubereturns.transcript.ytbsd-path:/app/scripts/ytbsd.py}")
    private String ytbsdPath;

    @Value("${tubereturns.transcript.transcripts-dir:/app/transcripts}")
    private String transcriptsDirPath;

    @Value("${tubereturns.transcript.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${tubereturns.pipeline.transcript.batch-size:1}")
    private int batchSize;

    private final ExecutorService ytbsdExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ytbsd-worker");
        t.setDaemon(true);
        return t;
    });

    private final LinkedList<List<String>> pendingBatches = new LinkedList<>();
    private final Set<String> queuedVideoIds = new HashSet<>();
    private final Object batchLock = new Object();
    private boolean draining = false;

    private Instant lastProxyRefreshAt = null;

    private final AtomicInteger ytbsdTotalRuns = new AtomicInteger();
    private final AtomicInteger ytbsdSuccessfulRuns = new AtomicInteger();
    private final AtomicInteger ytbsdFailedRuns = new AtomicInteger();
    private volatile Long ytbsdLastDurationMs = null;
    private volatile Integer ytbsdLastBatchSize = null;
    private volatile boolean ytbsdRunning = false;
    private volatile Integer ytbsdCurrentBatchSize = null;

    public int getQueueSize() {
        synchronized (batchLock) {
            return pendingBatches.stream().mapToInt(List::size).sum();
        }
    }

    public record YtbsdStats(int totalRuns, int successfulRuns, int failedRuns, Long lastDurationMs, Integer lastBatchSize, boolean running, Integer currentBatchSize) {}

    public YtbsdStats getYtbsdStats() {
        return new YtbsdStats(ytbsdTotalRuns.get(), ytbsdSuccessfulRuns.get(), ytbsdFailedRuns.get(), ytbsdLastDurationMs, ytbsdLastBatchSize, ytbsdRunning, ytbsdCurrentBatchSize);
    }

    @PreDestroy
    public void shutdown() {
        ytbsdExecutor.shutdown();
    }

    public int downloadPendingTranscripts() {
        return downloadPendingTranscripts(batchSize);
    }

    public int downloadPendingTranscripts(int batchSize) {
        List<Video> pendingVideos = videoRepository.findAllByTranscriptStatus(Video.TranscriptStatus.PENDING);
        if (pendingVideos.isEmpty()) {
            return 0;
        }
        log.info("Found {} video(s) pending transcript download", pendingVideos.size());
        enqueue(pendingVideos.stream().map(Video::getVideoId).toList(), batchSize);
        return pendingVideos.size();
    }

    public void downloadTranscript(Video video) {
        log.info("Enqueueing transcript download for: {} ({})", video.getTitle(), "https://youtu.be/" + video.getVideoId());
        enqueue(List.of(video.getVideoId()), batchSize);
    }

    private void enqueue(List<String> videoIds, int batchSize) {
        synchronized (batchLock) {
            List<String> toAdd = videoIds.stream()
                    .filter(id -> !queuedVideoIds.contains(id))
                    .toList();
            if (toAdd.isEmpty()) {
                return;
            }
            queuedVideoIds.addAll(toAdd);

            List<String> remaining = new ArrayList<>(toAdd);
            if (!pendingBatches.isEmpty()) {
                List<String> last = pendingBatches.getLast();
                int space = batchSize - last.size();
                if (space > 0) {
                    int n = Math.min(space, remaining.size());
                    last.addAll(remaining.subList(0, n));
                    remaining = new ArrayList<>(remaining.subList(n, remaining.size()));
                }
            }
            for (int i = 0; i < remaining.size(); i += batchSize) {
                pendingBatches.addLast(new ArrayList<>(remaining.subList(i, Math.min(i + batchSize, remaining.size()))));
            }
            log.info("Batch queue: {} item(s) pending after enqueue, draining={}", pendingBatches.size(), draining);
            if (!draining) {
                draining = true;
                ytbsdExecutor.submit(this::drainNextBatch);
            }
        }
    }

    private void drainNextBatch() {
        List<String> batch;
        synchronized (batchLock) {
            batch = pendingBatches.pollFirst();
            if (batch == null) {
                draining = false;
                return;
            }
        }

        processBatch(batch);

        synchronized (batchLock) {
            if (!pendingBatches.isEmpty()) {
                ytbsdExecutor.submit(this::drainNextBatch);
            } else {
                draining = false;
            }
        }
    }

    private void processBatch(List<String> videoIds) {
        log.info("Processing ytbsd batch of {} video(s): {}", videoIds.size(), videoIds);
        ytbsdRunning = true;
        ytbsdCurrentBatchSize = videoIds.size();
        TransactionTemplate tx = new TransactionTemplate(txManager);

        try {
            tx.executeWithoutResult(_ ->
                videoIds.forEach(videoId -> videoRepository.findByVideoId(videoId).ifPresent(v -> {
                    v.setTranscriptStatus(Video.TranscriptStatus.DOWNLOADING);
                    videoRepository.save(v);
                }))
            );

            try {
                invokeYtbsd(videoIds);
            } catch (Exception e) {
                log.error("ytbsd batch failed for {}: {}", videoIds, e.getMessage(), e);
                tx.executeWithoutResult(_ ->
                    videoIds.forEach(videoId -> videoRepository.findByVideoId(videoId).ifPresent(v -> {
                        v.setTranscriptStatus(Video.TranscriptStatus.FAILED);
                        videoRepository.save(v);
                    }))
                );
                return;
            }

            List<String> downloadedIds = new ArrayList<>();
            Path transcriptsDir = Path.of(transcriptsDirPath).toAbsolutePath();
            for (String videoId : videoIds) {
                String videoTitle = videoRepository.findByVideoId(videoId).map(Video::getTitle).orElse(videoId);
                try {
                    String transcript = readTranscriptFromOutput(transcriptsDir, videoId, videoTitle);
                    tx.executeWithoutResult(_ ->
                        videoRepository.findByVideoId(videoId).ifPresent(v -> {
                            if (transcript != null && !transcript.isBlank()) {
                                v.setTranscriptText(transcript);
                                v.setTranscriptStatus(Video.TranscriptStatus.DOWNLOADED);
                                downloadedIds.add(videoId);
                                log.info("Transcript downloaded: {} (https://youtu.be/{})", v.getTitle(), videoId);
                            } else {
                                v.setTranscriptStatus(Video.TranscriptStatus.NO_TRANSCRIPT);
                                log.warn("No transcript for: {} (https://youtu.be/{})", v.getTitle(), videoId);
                            }
                            videoRepository.save(v);
                        })
                    );
                } catch (Exception e) {
                    log.error("Error processing transcript output for {}: {}", videoId, e.getMessage(), e);
                    tx.executeWithoutResult(_ ->
                        videoRepository.findByVideoId(videoId).ifPresent(v -> {
                            v.setTranscriptStatus(Video.TranscriptStatus.FAILED);
                            videoRepository.save(v);
                        })
                    );
                }
            }
            downloadedIds.forEach(extractionService::enqueueForProcessing);
        } finally {
            ytbsdRunning = false;
            ytbsdCurrentBatchSize = null;
            synchronized (batchLock) {
                videoIds.forEach(queuedVideoIds::remove);
            }
        }
    }

    private void invokeYtbsd(List<String> videoIds) throws IOException, InterruptedException {
        boolean skipProxyRefresh = lastProxyRefreshAt != null &&
                Duration.between(lastProxyRefreshAt, Instant.now()).toMinutes() < 10;

        if (skipProxyRefresh) {
            log.info("Skipping proxy refresh — last refresh was {} min ago", Duration.between(lastProxyRefreshAt, Instant.now()).toMinutes());
        } else {
            log.info("Refreshing proxies (last refresh: {})", lastProxyRefreshAt != null ? lastProxyRefreshAt : "never");
            lastProxyRefreshAt = Instant.now();
        }

        int threads = Math.min(videoIds.size() * 20, 300);
        List<String> cmd = new ArrayList<>(List.of("python3", ytbsdPath, "--mode", "batch", "--threads", String.valueOf(threads)));
        if (skipProxyRefresh) {
            cmd.add("--no-proxy-refresh");
        }
        cmd.add("--urls");
        videoIds.stream().map(id -> "https://youtu.be/" + id).forEach(cmd::add);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        log.info("Running ytbsd.py: {}", cmd);
        Instant start = Instant.now();
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        ytbsdTotalRuns.incrementAndGet();
        ytbsdLastBatchSize = videoIds.size();
        ytbsdLastDurationMs = durationMs;

        if (!finished) {
            process.destroyForcibly();
            ytbsdFailedRuns.incrementAndGet();
            throw new RuntimeException("ytbsd.py timed out after " + timeoutSeconds + "s for videos " + videoIds);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            ytbsdFailedRuns.incrementAndGet();
            log.warn("ytbsd.py exited with code {} for {}:\n{}", exitCode, videoIds, output);
            throw new RuntimeException("ytbsd.py failed for videos " + videoIds + ": " + output.trim());
        }

        ytbsdSuccessfulRuns.incrementAndGet();
        log.info("ytbsd.py output for {}:\n{}", videoIds, output);
    }

    private String readTranscriptFromOutput(Path transcriptsDir, String videoId, String videoTitle) throws IOException {
        Path mdFile = findOutputFile(transcriptsDir, videoId);
        if (mdFile == null) {
            log.warn("No output file found for {} (https://youtu.be/{})", videoTitle, videoId);
            return null;
        }
        try {
            String markdown = Files.readString(mdFile);
            String transcript = parseMarkdownTranscript(markdown);
            if (transcript != null) {
                log.info("Transcript for {} (https://youtu.be/{}) — {} chars: {}", videoTitle, videoId, transcript.length(),
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
