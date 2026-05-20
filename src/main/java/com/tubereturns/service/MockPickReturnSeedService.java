package com.tubereturns.service;

import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.PickRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Service
@Profile("dev")
public class MockPickReturnSeedService {

    private final ChannelRepository channelRepository;
    private final PickRepository pickRepository;
    private final PickPerformanceService pickPerformanceService;
    private final MockChannelPriceSeedService mockChannelPriceSeedService;
    private final SpyHistoricalSeedService spyHistoricalSeedService;
    private final StartupCoordinator startupCoordinator;

    private CompletableFuture<Void> initTask;

    @PostConstruct
    void init() {
        initTask = startupCoordinator.register();
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void computeReturns() {
        try {
            CompletableFuture.allOf(
                    mockChannelPriceSeedService.getInitTask(),
                    spyHistoricalSeedService.getInitTask()
            ).join();

            int count = 0;
            for (var channel : channelRepository.findAll()) {
                if (!channel.getHandle().startsWith("mock-")) {
                    continue;
                }
                for (var pick : pickRepository.findPicksByChannelId(channel.getId())) {
                    pickPerformanceService.computeAndSaveReturns(pick);
                    count++;
                }
            }
            log.info("Mock pick return seed complete — processed {} pick(s)", count);
        } catch (Exception e) {
            log.error("Mock pick return seed failed: {}", e.getMessage(), e);
        } finally {
            initTask.complete(null);
        }
    }
}
