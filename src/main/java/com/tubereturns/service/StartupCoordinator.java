package com.tubereturns.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class StartupCoordinator {

    private final List<CompletableFuture<Void>> tasks = Collections.synchronizedList(new ArrayList<>());

    public CompletableFuture<Void> register() {
        CompletableFuture<Void> f = new CompletableFuture<>();
        tasks.add(f);
        return f;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onReady() {
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                .thenRun(() -> log.info("Init completed"));
    }
}
