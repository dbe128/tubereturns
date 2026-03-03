package com.tubereturns.service;

import com.tubereturns.model.Performance;
import com.tubereturns.model.Pick;
import com.tubereturns.repository.PerformanceRepository;
import com.tubereturns.repository.PickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@Transactional
public class StockPerformanceService {

    private static final Logger logger = LoggerFactory.getLogger(StockPerformanceService.class);

    @Value("${tubereturns.stock-data.enabled:false}")
    private boolean stockDataEnabled;

    @Value("${tubereturns.stock-data.api-key:}")
    private String apiKey;

    @Value("${tubereturns.performance.mock-mode:true}")
    private boolean mockMode;

    private final PickRepository pickRepository;
    private final PerformanceRepository performanceRepository;
    private final StockDataService stockDataService;
    private final Random random = new Random();

    public StockPerformanceService(PickRepository pickRepository,
                                 PerformanceRepository performanceRepository,
                                 StockDataService stockDataService) {
        this.pickRepository = pickRepository;
        this.performanceRepository = performanceRepository;
        this.stockDataService = stockDataService;
    }

    public void calculatePerformanceForNewPicks() {
        List<Pick> picksWithoutPerformance = pickRepository.findPicksWithoutPerformance();
        logger.info("Found {} picks without performance data", picksWithoutPerformance.size());

        for (Pick pick : picksWithoutPerformance) {
            try {
                calculatePerformance(pick);
            } catch (Exception e) {
                logger.error("Error calculating performance for pick {}: {}", pick.getId(), e.getMessage(), e);
            }
        }
    }

    public void updateStalePerformanceData() {
        Instant staleThreshold = Instant.now().minus(1, ChronoUnit.HOURS);
        List<Performance> stalePerformance = performanceRepository.findPerformanceNeedingUpdate(staleThreshold);

        logger.info("Found {} performance records needing update", stalePerformance.size());

        for (Performance performance : stalePerformance) {
            try {
                updatePerformance(performance);
            } catch (Exception e) {
                logger.error("Error updating performance {}: {}", performance.getId(), e.getMessage(), e);
            }
        }
    }

    public Performance calculatePerformance(Pick pick) {
        logger.info("Calculating performance for pick: {} {} ({})",
                   pick.getSignal(), pick.getTickerSymbol(), pick.getId());

        Performance performance = new Performance(pick);

        if (!stockDataEnabled || mockMode) {
            logger.info("Using mock performance data for pick {}", pick.getId());
            setMockPerformanceData(performance, pick);
        } else {
            setRealPerformanceData(performance, pick);
        }

        return performanceRepository.save(performance);
    }

    public Performance updatePerformance(Performance performance) {
        Pick pick = performance.getPick();
        logger.debug("Updating performance for pick: {} {} ({})",
                    pick.getSignal(), pick.getTickerSymbol(), pick.getId());

        if (!stockDataEnabled || mockMode) {
            setMockPerformanceData(performance, pick);
        } else {
            setRealPerformanceData(performance, pick);
        }

        return performanceRepository.save(performance);
    }

    private void setMockPerformanceData(Performance performance, Pick pick) {
        BigDecimal basePrice = generateMockBasePrice(pick.getTickerSymbol());
        performance.setStartPrice(basePrice);

        BigDecimal currentPrice = generateMockCurrentPrice(basePrice);
        performance.setCurrentPrice(currentPrice);

        Instant pickDate = pick.getExtractionTimestamp();
        Instant now = Instant.now();

        if (pickDate != null) {
            performance.setReturn1d(calculateMockReturn(0.02));
            performance.setReturn7d(calculateMockReturn(0.05));
            performance.setReturn30d(calculateMockReturn(0.10));
            performance.setReturn90d(calculateMockReturn(0.15));
            performance.setReturn1y(calculateMockReturn(0.20));
            performance.setReturnYtd(calculateMockReturn(0.12));
        }

        performance.setLastUpdated(now);
    }

    private void setRealPerformanceData(Performance performance, Pick pick) {
        try {
            String ticker = pick.getTickerSymbol();
            Instant pickDate = pick.getExtractionTimestamp();

            Optional<BigDecimal> startPrice = stockDataService.getHistoricalPrice(ticker, pickDate);
            Optional<BigDecimal> currentPrice = stockDataService.getCurrentPrice(ticker);

            if (startPrice.isPresent() && currentPrice.isPresent()) {
                performance.setStartPrice(startPrice.get());
                performance.setCurrentPrice(currentPrice.get());

                performance.setReturn1d(stockDataService.getReturn(ticker, 1).orElse(null));
                performance.setReturn7d(stockDataService.getReturn(ticker, 7).orElse(null));
                performance.setReturn30d(stockDataService.getReturn(ticker, 30).orElse(null));
                performance.setReturn90d(stockDataService.getReturn(ticker, 90).orElse(null));
                performance.setReturn1y(stockDataService.getReturn(ticker, 365).orElse(null));
                performance.setReturnYtd(stockDataService.getYtdReturn(ticker).orElse(null));

                performance.setLastUpdated(Instant.now());
            } else {
                logger.warn("Could not fetch real stock data for {}, using mock data", ticker);
                setMockPerformanceData(performance, pick);
            }
        } catch (Exception e) {
            logger.error("Error fetching real stock data for {}: {}", pick.getTickerSymbol(), e.getMessage());
            setMockPerformanceData(performance, pick);
        }
    }

    private BigDecimal generateMockBasePrice(String ticker) {
        return switch (ticker.toUpperCase()) {
            case "AAPL" -> BigDecimal.valueOf(150.00 + (random.nextDouble() * 50));
            case "TSLA" -> BigDecimal.valueOf(200.00 + (random.nextDouble() * 100));
            case "MSFT" -> BigDecimal.valueOf(300.00 + (random.nextDouble() * 50));
            case "GOOGL" -> BigDecimal.valueOf(120.00 + (random.nextDouble() * 30));
            case "AMZN" -> BigDecimal.valueOf(140.00 + (random.nextDouble() * 40));
            case "META" -> BigDecimal.valueOf(250.00 + (random.nextDouble() * 100));
            case "NVDA" -> BigDecimal.valueOf(400.00 + (random.nextDouble() * 200));
            default -> BigDecimal.valueOf(50.00 + (random.nextDouble() * 150));
        };
    }

    private BigDecimal generateMockCurrentPrice(BigDecimal basePrice) {
        double changePercent = (random.nextDouble() - 0.5) * 0.4;
        BigDecimal change = basePrice.multiply(BigDecimal.valueOf(changePercent));
        return basePrice.add(change).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMockReturn(double volatility) {
        double returnValue = (random.nextGaussian() * volatility);
        return BigDecimal.valueOf(returnValue).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateActualReturn(BigDecimal startPrice, BigDecimal currentPrice) {
        if (startPrice == null || currentPrice == null ||
            startPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return currentPrice.subtract(startPrice)
                .divide(startPrice, 4, RoundingMode.HALF_UP);
    }

    public List<Performance> getTopPerformingPicksByChannel(Long channelId) {
        return performanceRepository.findBestPerformingPicksByChannel(channelId);
    }

    public Double getAverageReturn30dByChannel(Long channelId) {
        return performanceRepository.findAverageReturn30dByChannel(channelId);
    }
}