package com.tubereturns.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PipelineStatusRegistry {

    private final MeterRegistry meterRegistry;

    private final Map<String, Instant> lastStartedAt = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastFinishedAt = new ConcurrentHashMap<>();
    private final Map<String, Boolean> running = new ConcurrentHashMap<>();
    private final Map<String, String> cronExpressions = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastRunCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> limits = new ConcurrentHashMap<>();
    private final Map<String, String> fatalErrors = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRunDurationMs = new ConcurrentHashMap<>();

    public void registerStep(String step, String cron, Integer limit) {
        cronExpressions.put(step, cron);
        if (limit != null) {
            limits.put(step, limit);
        }
        Gauge.builder("tubereturns.pipeline.running", running, m -> Boolean.TRUE.equals(m.get(step)) ? 1.0 : 0.0)
             .tag("step", step).register(meterRegistry);
        Gauge.builder("tubereturns.pipeline.last.duration.ms", lastRunDurationMs, m -> m.getOrDefault(step, 0L).doubleValue())
             .tag("step", step).register(meterRegistry);
    }

    public void markStarted(String step) {
        lastStartedAt.put(step, Instant.now());
        running.put(step, true);
        fatalErrors.remove(step);
        meterRegistry.counter("tubereturns.pipeline.runs", "step", step).increment();
    }

    public void markFinished(String step, int count) {
        Instant now = Instant.now();
        Instant started = lastStartedAt.get(step);
        if (started != null) {
            lastRunDurationMs.put(step, Duration.between(started, now).toMillis());
        }
        lastFinishedAt.put(step, now);
        lastRunCounts.put(step, count);
        running.put(step, false);
    }

    public void markFatalError(String step, String reason) {
        lastFinishedAt.put(step, Instant.now());
        running.put(step, false);
        fatalErrors.put(step, reason);
    }

    public String getFatalError(String step) {
        return fatalErrors.get(step);
    }

    public void markProgress(String step, int count) {
        lastFinishedAt.put(step, Instant.now());
        lastRunCounts.put(step, count);
    }


    public boolean isRunning(String step) {
        return Boolean.TRUE.equals(running.get(step));
    }

    public String getLastStartedAt(String step) {
        Instant t = lastStartedAt.get(step);
        return t != null ? t.toString() : null;
    }

    public String getLastFinishedAt(String step) {
        Instant t = lastFinishedAt.get(step);
        return t != null ? t.toString() : null;
    }

    public String getNextRunAt(String step) {
        String cron = cronExpressions.get(step);
        if (cron == null) {
            return null;
        }
        try {
            ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.now());
            return next != null ? next.toInstant().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public Integer getLastRunCount(String step) {
        return lastRunCounts.get(step);
    }

    public Integer getLimit(String step) {
        return limits.get(step);
    }

    public Long getLastRunDurationMs(String step) {
        return lastRunDurationMs.get(step);
    }
}
