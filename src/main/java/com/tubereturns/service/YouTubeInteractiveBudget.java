package com.tubereturns.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@RequiredArgsConstructor
@Service
public class YouTubeInteractiveBudget {

    private static final ZoneId QUOTA_RESET_ZONE = ZoneId.of("America/Los_Angeles");

    private final MeterRegistry meterRegistry;

    @Value("${tubereturns.youtube.interactive-quota.anonymous-daily-units}")
    private long anonymousDailyUnits;

    @Value("${tubereturns.youtube.interactive-quota.authenticated-daily-units}")
    private long authenticatedDailyUnits;

    private LocalDate currentDay;
    private long anonymousUsed;
    private long authenticatedUsed;

    public synchronized boolean tryConsume(boolean authenticated, long units) {
        LocalDate today = LocalDate.now(QUOTA_RESET_ZONE);
        if (!today.equals(currentDay)) {
            currentDay = today;
            anonymousUsed = 0;
            authenticatedUsed = 0;
        }
        long used = authenticated ? authenticatedUsed : anonymousUsed;
        long limit = authenticated ? authenticatedDailyUnits : anonymousDailyUnits;
        if (used + units > limit) {
            meterRegistry.counter("tubereturns.youtube.interactive.rejected",
                    "caller", authenticated ? "authenticated" : "anonymous").increment();
            log.warn("Interactive YouTube quota budget exhausted for {} callers ({}/{} units used)",
                    authenticated ? "authenticated" : "anonymous", used, limit);
            return false;
        }
        if (authenticated) {
            authenticatedUsed += units;
        } else {
            anonymousUsed += units;
        }
        return true;
    }
}
