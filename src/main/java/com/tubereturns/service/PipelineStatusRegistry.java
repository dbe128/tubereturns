package com.tubereturns.service;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PipelineStatusRegistry {

    private final Map<String, Instant> lastStartedAt = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastFinishedAt = new ConcurrentHashMap<>();
    private final Map<String, Boolean> running = new ConcurrentHashMap<>();
    private final Map<String, String> cronExpressions = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastRunCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> limits = new ConcurrentHashMap<>();

    public void registerStep(String step, String cron, int limit) {
        cronExpressions.put(step, cron);
        limits.put(step, limit);
    }

    public void markStarted(String step) {
        lastStartedAt.put(step, Instant.now());
        running.put(step, true);
    }

    public void markFinished(String step, int count) {
        lastFinishedAt.put(step, Instant.now());
        lastRunCounts.put(step, count);
        running.put(step, false);
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

    public int getLimit(String step) {
        return limits.getOrDefault(step, 0);
    }
}
