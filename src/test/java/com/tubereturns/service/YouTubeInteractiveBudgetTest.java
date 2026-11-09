package com.tubereturns.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class YouTubeInteractiveBudgetTest {

    private final YouTubeInteractiveBudget budget = new YouTubeInteractiveBudget(new SimpleMeterRegistry());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(budget, "anonymousDailyUnits", 200L);
        ReflectionTestUtils.setField(budget, "authenticatedDailyUnits", 300L);
    }

    @Test
    void rejectsOnceTheDailyUnitsAreSpent() {
        assertThat(budget.tryConsume(false, 101)).isTrue();
        assertThat(budget.tryConsume(false, 101)).isFalse();
        assertThat(budget.tryConsume(false, 99)).isTrue();
        assertThat(budget.tryConsume(false, 1)).isFalse();
    }

    @Test
    void anonymousExhaustionDoesNotConsumeTheAuthenticatedBudget() {
        assertThat(budget.tryConsume(false, 200)).isTrue();
        assertThat(budget.tryConsume(false, 1)).isFalse();
        assertThat(budget.tryConsume(true, 300)).isTrue();
        assertThat(budget.tryConsume(true, 1)).isFalse();
    }
}
