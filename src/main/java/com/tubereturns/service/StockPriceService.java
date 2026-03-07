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

@Slf4j
@Service
public class StockPriceService {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, Double> cache = new ConcurrentHashMap<>();

    public static double getClosePrice(String ticker, LocalDate date) throws Exception {

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
                        .GET()
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

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