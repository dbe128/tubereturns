package com.tubereturns.controller;

import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.CurrencyRepository;
import com.tubereturns.repository.PickRepository;
import com.tubereturns.repository.StockRepository;
import com.tubereturns.service.AiModelService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final PickRepository pickRepository;
    private final ChannelRepository channelRepository;
    private final StockRepository stockRepository;
    private final CurrencyRepository currencyRepository;
    private final AiModelService aiModelService;
    private final BuildProperties buildProperties;

    @Lazy
    @Autowired
    private StatsController self;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        self.getStats();
    }

    @GetMapping
    @Cacheable("siteStats")
    @Operation(summary = "Get site-wide stats")
    public Map<String, Object> getStats() {
        return Map.of(
                "totalPicks", pickRepository.count(),
                "totalChannels", channelRepository.count(),
                "totalStocks", stockRepository.count(),
                "totalCurrencies", currencyRepository.count(),
                "totalLlmModels", (long) aiModelService.getModelCount(),
                "totalDeletedVideos", channelRepository.sumArchivarixDeletedCount(),
                "version", buildProperties.getVersion()
        );
    }
}
