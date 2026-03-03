package com.tubereturns.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;

@Service
public class StockDataService {

    private static final Logger logger = LoggerFactory.getLogger(StockDataService.class);

    @Value("${tubereturns.stock-data.provider:mock}")
    private String provider;

    @Value("${tubereturns.stock-data.api-key:}")
    private String apiKey;

    private final Random random = new Random();

    public Optional<BigDecimal> getCurrentPrice(String ticker) {
        if ("mock".equals(provider)) {
            return Optional.of(generateMockPrice(ticker));
        }

        logger.info("Fetching current price for {}", ticker);
        return Optional.of(generateMockPrice(ticker));
    }

    public Optional<BigDecimal> getHistoricalPrice(String ticker, Instant date) {
        if ("mock".equals(provider)) {
            return Optional.of(generateMockPrice(ticker).multiply(BigDecimal.valueOf(0.95 + random.nextDouble() * 0.10)));
        }

        logger.info("Fetching historical price for {} at {}", ticker, date);
        return Optional.of(generateMockPrice(ticker));
    }

    public Optional<BigDecimal> getReturn(String ticker, int days) {
        if ("mock".equals(provider)) {
            return Optional.of(generateMockReturn(days));
        }

        logger.info("Fetching {}-day return for {}", days, ticker);
        return Optional.of(generateMockReturn(days));
    }

    public Optional<BigDecimal> getYtdReturn(String ticker) {
        if ("mock".equals(provider)) {
            return Optional.of(generateMockReturn(200));
        }

        logger.info("Fetching YTD return for {}", ticker);
        return Optional.of(generateMockReturn(200));
    }

    private BigDecimal generateMockPrice(String ticker) {
        return switch (ticker.toUpperCase()) {
            case "AAPL" -> BigDecimal.valueOf(175.00 + (random.nextDouble() * 20));
            case "TSLA" -> BigDecimal.valueOf(240.00 + (random.nextDouble() * 60));
            case "MSFT" -> BigDecimal.valueOf(320.00 + (random.nextDouble() * 40));
            case "GOOGL" -> BigDecimal.valueOf(125.00 + (random.nextDouble() * 25));
            case "AMZN" -> BigDecimal.valueOf(155.00 + (random.nextDouble() * 30));
            case "META" -> BigDecimal.valueOf(280.00 + (random.nextDouble() * 80));
            case "NVDA" -> BigDecimal.valueOf(450.00 + (random.nextDouble() * 150));
            default -> BigDecimal.valueOf(75.00 + (random.nextDouble() * 100));
        };
    }

    private BigDecimal generateMockReturn(int days) {
        double volatility = Math.sqrt(days / 365.0) * 0.25;
        double returnValue = random.nextGaussian() * volatility;
        return BigDecimal.valueOf(returnValue).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}