package com.tubereturns.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class StockPriceService {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, Double> cache = new ConcurrentHashMap<>();

    /** Non-null when rate-limited; fetches are blocked until this instant. */
    private static final AtomicReference<Instant> rateLimitedUntil = new AtomicReference<>(null);

    public static double getClosePrice(String ticker, LocalDate date) throws Exception {

        Instant blockedUntil = rateLimitedUntil.get();
        if (blockedUntil != null) {
            if (Instant.now().isBefore(blockedUntil)) {
                throw new IllegalStateException(
                        "Yahoo Finance rate-limited — fetches suspended until " + blockedUntil);
            } else {
                rateLimitedUntil.set(null);
                log.info("Yahoo Finance rate-limit window has passed, resuming fetches.");
            }
        }

        date = adjustForWeekend(date);

        String cacheKey = ticker + "_" + date;

        if (cache.containsKey(cacheKey)) {
            Double price = cache.get(cacheKey);
            log.info("Returning cached price {} for {} on {}. ", price, ticker, date);
            return price;
        }

        double price = fetchClosePrice(ticker, date);

        cache.put(cacheKey, price);

        return price;
    }

    public static Map<LocalDate, Double> fetchHistoricalClosePrices(String ticker, LocalDate from, LocalDate to) throws Exception {
        long start = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        String url = String.format(
                "https://query1.finance.yahoo.com/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d",
                ticker, start, end
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .GET()
                .build();

        log.info("Fetching historical prices for {} from {} to {}", ticker, from, to);

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) {
            throw new IllegalStateException("Yahoo Finance rate limit hit while fetching historical data for " + ticker);
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode result = root.path("chart").path("result").get(0);

        JsonNode timestamps = result.path("timestamp");
        JsonNode closes = result.path("indicators").path("quote").get(0).path("close");

        Map<LocalDate, Double> prices = new java.util.LinkedHashMap<>();
        for (int i = 0; i < timestamps.size(); i++) {
            JsonNode closeNode = closes.get(i);
            if (closeNode == null || closeNode.isNull()) {
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(timestamps.get(i).asLong())
                    .atZone(ZoneOffset.UTC).toLocalDate();
            prices.put(date, closeNode.asDouble());
        }

        log.info("Fetched {} historical price points for {}", prices.size(), ticker);
        return prices;
    }

    private static double fetchClosePrice(String ticker, LocalDate date) throws Exception {

        long start = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        String url = String.format(
                "https://query1.finance.yahoo.com/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d",
                ticker, start, end
        );

        int retries = 3;

        while (retries-- > 0) {

            try {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .GET()
                        .build();

                log.debug("Stock price request: GET {}", url);

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                log.debug("Stock price response: status={} body={}", response.statusCode(), response.body());

                if (response.statusCode() == 429) {
                    Instant nextHour = ZonedDateTime.now(ZoneOffset.UTC)
                            .truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                            .plusHours(1)
                            .toInstant();
                    rateLimitedUntil.set(nextHour);
                    log.warn("Yahoo Finance returned 429 (rate limited) for {} on {}. "
                            + "All price fetches suspended until {}.", ticker, date, nextHour);
                    throw new IllegalStateException("Yahoo Finance rate limit hit — suspended until " + nextHour);
                }

                JsonNode root = mapper.readTree(response.body());

                JsonNode closeNode = root
                        .path("chart")
                        .path("result")
                        .get(0)
                        .path("indicators")
                        .path("quote")
                        .get(0)
                        .path("close")
                        .get(0);

                if (!closeNode.isMissingNode()) {
                    log.info("Fetched price {} for {} on {}. ", closeNode.asDouble(), ticker, date);
                    return closeNode.asDouble();
                }

            } catch (Exception e) {
                log.error("Stock price query failed for {} on {}. Number of retries left: {}", ticker, date, retries);
                if (retries == 0) {
                    throw e;
                }

                Thread.sleep(500);
            }
        }

        throw new RuntimeException("Failed to fetch price");
    }

    private static LocalDate adjustForWeekend(LocalDate date) {

        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }

        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.minusDays(2);
        }

        return date;
    }
}