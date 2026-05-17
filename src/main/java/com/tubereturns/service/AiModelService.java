package com.tubereturns.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class AiModelService {

    private static final String EXTRACTION_PROMPT_TEMPLATE = """
        Analyze the following YouTube video transcript and extract stock picks mentioned by the creator.

        Video title: %s

        Look for:
        1. Stock ticker symbols
        2. Company names being discussed as investments
        3. Explicit BUY or SELL recommendations made by the video creator
        4. Implicit signals based on the creator's own analysis or opinion — see signal rules below

        Output ONLY the raw JSON object below — no explanation, no markdown, no code fences, no text before or after:
        {
          "videoId": "PLACEHOLDER",
          "externalPositions": false,
          "extractions": [
            {
              "tickerSymbol": "TICKER",
              "companyName": "Company Name",
              "currency": "USD",
              "signal": "BUY"
            }
          ]
        }

        Rules:
        - You MUST include EVERY investment recommendation mentioned in the transcript — do not skip or summarise any
        - signal must be either "BUY" or "SELL"
        - tickerSymbol must be the current, up-to-date ticker — use the latest symbol after any rebranding or ticker change (e.g. META not FB, GOOGL not GOOG if that was the old symbol)
        - tickerSymbol and currency must both reflect the stock's primary listing exchange — the main exchange where the company is headquartered and primarily traded, not a secondary or cross-listing
        - Exception: for stocks from outside the US and Europe, prefer the ADR ticker on NYSE/NASDAQ (no suffix, currency USD) if one exists — e.g. TSM instead of 2330.TW, BABA instead of 9988.HK, SONY instead of 6758.T
        - tickerSymbol must be in Yahoo Finance format:
          - US stocks (NYSE, NASDAQ, etc.): no suffix — e.g. AAPL, TSLA, FL
          - German stocks (XETRA): append .DE — e.g. VOW3.DE, SAP.DE
          - UK stocks (LSE): append .L — e.g. BARC.L, SHEL.L
          - Australian stocks (ASX): append .AX — e.g. CBA.AX, BHP.AX
          - French stocks (Euronext Paris): append .PA — e.g. AIR.PA, MC.PA
          - Dutch stocks (Euronext Amsterdam): append .AS — e.g. ASML.AS, PHIA.AS
          - Canadian stocks (TSX): append .TO — e.g. RY.TO, TD.TO
          - Japanese stocks (TSE): append .T — e.g. 7203.T, 6758.T (only if no ADR exists)
          - Swiss stocks (SIX): append .SW — e.g. NESN.SW, NOVN.SW
          - Hong Kong stocks (HKEX): append .HK — e.g. 0700.HK, 9988.HK (only if no ADR exists)
        - currency must be the 3-letter ISO currency code of the chosen exchange — USD for ADRs and US-listed stocks, otherwise the local currency
        - If no picks are found, return an empty extractions array
        - Set externalPositions to true if the transcript only presents positions or trades made by someone else (another person, an AI agent, a portfolio manager, etc.) rather than the video creator's own picks — the creator is merely reporting or reviewing them, not recommending them personally
        - Implicit BUY signals: the creator expresses that a stock is undervalued, attractively priced, a good investment, a compelling opportunity, has strong upside, or otherwise indicates bullish conviction based on their own analysis — treat this as BUY
        - Implicit SELL signals: the creator expresses that a stock is overvalued, too expensive, a poor investment, has limited upside or significant downside risk, or otherwise indicates bearish conviction based on their own analysis — treat this as SELL
        - Only apply implicit signals when the creator is expressing their own view, not when merely describing the stock or reporting others' opinions

        Transcript:
        """;

    @Value("${tubereturns.ai.api-key}")
    private String apiKey;

    @Value("${tubereturns.ai.models-file}")
    private String modelsFilePath;

    @Value("${tubereturns.ai.model-reset-minutes}")
    private int modelResetMinutes;

    @Value("${tubereturns.ai.connect-timeout-seconds}")
    private int connectTimeoutSeconds;

    @Value("${tubereturns.ai.read-timeout-seconds}")
    private int readTimeoutSeconds;

    @Value("${tubereturns.ai.timeout-retries}")
    private int timeoutRetries;

    private List<String> models;
    private final ThreadLocal<Integer> currentModelIndex = ThreadLocal.withInitial(() -> 0);
    private volatile int lastKnownModelIndex = 0;
    private volatile Instant lastModel0AttemptAt = null;
    private volatile Long lastCallDurationMs = null;

    private RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void loadModels() {
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .build());
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        restClient = RestClient.builder().requestFactory(factory).build();
        log.info("OpenRouter HTTP client configured: connectTimeout={}s readTimeout={}s timeoutRetries={}",
                connectTimeoutSeconds, readTimeoutSeconds, timeoutRetries);
        Path path = Path.of(modelsFilePath);
        if (Files.exists(path)) {
            try {
                models = new CopyOnWriteArrayList<>(Files.readAllLines(path).stream()
                        .map(String::strip)
                        .filter(l -> !l.isBlank() && !l.startsWith("#"))
                        .toList());
                log.info("Loaded {} AI model(s) from {}: first={}", models.size(), modelsFilePath, models.isEmpty() ? "none" : models.getFirst());
            } catch (Exception e) {
                log.error("Failed to read models file {}: {}", modelsFilePath, e.getMessage());
                models = new CopyOnWriteArrayList<>(List.of("openrouter/owl-alpha"));
            }
        } else {
            log.warn("Models file not found at {} — using default model", modelsFilePath);
            models = List.of("openrouter/owl-alpha");
        }
        for (String model : models) {
            meterRegistry.counter("tubereturns.ai.rate.limits", "model", model);
            meterRegistry.counter("tubereturns.ai.timeouts", "model", model);
            meterRegistry.counter("tubereturns.ai.payment.required", "model", model);
            meterRegistry.timer("tubereturns.ai.call", "model", model);
        }
    }

    public record ExtractionResult(String content, String model) {}

    public record AiModelStatus(int currentIndex, String currentModel, Instant model0ResetAt, Long lastCallDurationMs) {}

    public AiModelStatus getStatus() {
        int idx = lastKnownModelIndex;
        String model = (models != null && !models.isEmpty()) ? models.get(idx % models.size()) : "unknown";
        Instant resetAt = lastModel0AttemptAt != null && idx > 0
                ? lastModel0AttemptAt.plusSeconds(modelResetMinutes * 60L)
                : null;
        return new AiModelStatus(idx, model, resetAt, lastCallDurationMs);
    }

    public ExtractionResult extractStockPicks(String videoId, String videoTitle, String transcriptText) {
        return callOpenRouter(videoId, videoTitle, transcriptText);
    }

    public int getCurrentModelIndex() {
        return currentModelIndex.get() % models.size();
    }

    public void setModelIndex(int index) {
        currentModelIndex.set(index);
    }

    public void advanceModel() {
        int size = models.size();
        if (size > 1) {
            int next = (currentModelIndex.get() + 1) % size;
            currentModelIndex.set(next);
            lastKnownModelIndex = next;
            log.info("Advanced AI model to index {} ({})", next, models.get(next));
        }
    }

    private ExtractionResult callOpenRouter(String videoId, String videoTitle, String transcriptText) {
        if (currentModelIndex.get() > 0) {
            boolean neverTriedModel0 = lastModel0AttemptAt == null;
            boolean model0CooledDown = !neverTriedModel0 && Duration.between(lastModel0AttemptAt, Instant.now()).toMinutes() >= modelResetMinutes;
            if (neverTriedModel0 || model0CooledDown) {
                long minutesAgo = neverTriedModel0 ? -1 : Duration.between(lastModel0AttemptAt, Instant.now()).toMinutes();
                log.info("Resetting to first model '{}' — {} minutes since last attempt (threshold: {})",
                        models.getFirst(), neverTriedModel0 ? "never tried" : minutesAgo, modelResetMinutes);
                currentModelIndex.set(0);
            }
        }

        int maxAttempts = models.size();
        int attemptsWithoutRemoval = 0;

        while (!models.isEmpty() && attemptsWithoutRemoval < maxAttempts) {
            int idx = currentModelIndex.get() % models.size();
            String model = models.get(idx);
            lastKnownModelIndex = idx;
            if (idx == 0) {
                lastModel0AttemptAt = Instant.now();
            }
            try {
                return callWithModel(model, videoId, videoTitle, transcriptText);
            } catch (RateLimitedException e) {
                meterRegistry.counter("tubereturns.ai.rate.limits", "model", model).increment();
                int nextIdx = (idx + 1) % models.size();
                currentModelIndex.set(nextIdx);
                lastKnownModelIndex = nextIdx;
                attemptsWithoutRemoval++;
                if (attemptsWithoutRemoval < maxAttempts) {
                    log.warn("Rate limited on model {} — switching to {}", model, models.get(nextIdx));
                } else {
                    log.error("Rate limited on model {} — all {} models exhausted", model, models.size());
                }
            } catch (TimedOutException e) {
                meterRegistry.counter("tubereturns.ai.timeouts", "model", model).increment();
                int nextIdx = (idx + 1) % models.size();
                currentModelIndex.set(nextIdx);
                lastKnownModelIndex = nextIdx;
                attemptsWithoutRemoval++;
                if (attemptsWithoutRemoval < maxAttempts) {
                    log.warn("Timed out on model {} after {} retries — switching to {}", model, timeoutRetries, models.get(nextIdx));
                } else {
                    log.error("Timed out on model {} — all {} models exhausted", model, models.size());
                }
            } catch (PaymentRequiredException e) {
                meterRegistry.counter("tubereturns.ai.payment.required", "model", model).increment();
                models.remove(idx);
                if (models.isEmpty()) {
                    log.error("Payment required on model {} — no more models available", model);
                    throw new RuntimeException("All AI models have exhausted their credits for video " + videoId);
                }
                int safeIdx = idx % models.size();
                currentModelIndex.set(safeIdx);
                lastKnownModelIndex = safeIdx;
                maxAttempts = models.size();
                attemptsWithoutRemoval = 0;
                log.warn("Payment required on model {} — removed from model list, {} remaining: {}", model, models.size(), models);
            }
        }
        throw new RuntimeException("All AI models exhausted for video " + videoId);
    }

    private ExtractionResult callWithModel(String model, String videoId, String videoTitle, String transcriptText) {
        log.info("Calling OpenRouter API with model {} — {} (https://youtu.be/{})", model, videoTitle, videoId);

        String prompt = EXTRACTION_PROMPT_TEMPLATE.formatted(videoTitle) + transcriptText;
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", "You are a financial data extraction engine. You output only raw JSON — no markdown, no code fences, no explanation, nothing else."),
                Map.of("role", "user", "content", prompt)
            )
        );

        int attemptsLeft = timeoutRetries;
        while (true) {
            String response = null;
            try {
                Instant callStart = Instant.now();
                response = restClient.post()
                    .uri("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
                lastCallDurationMs = Duration.between(callStart, Instant.now()).toMillis();

                JsonNode root = objectMapper.readTree(response);
                String actualModel = root.path("model").asText(model);
                log.info("OpenRouter used model: {} — call took {}ms", actualModel, lastCallDurationMs);
                meterRegistry.timer("tubereturns.ai.call", "model", actualModel)
                              .record(java.time.Duration.ofMillis(lastCallDurationMs));
                String text = root.path("choices").get(0).path("message").path("content").asText();
                return new ExtractionResult(stripJsonFences(text), actualModel);

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    throw new RateLimitedException(model);
                }
                if (e.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
                    log.error("OpenRouter returned 402 Payment Required — insufficient credits");
                    throw new PaymentRequiredException("OpenRouter API returned 402: insufficient credits");
                }
                log.error("OpenRouter API call failed with model {}: {}\nResponse: {}", model, e.getMessage(), response != null ? response.strip() : null, e);
                throw new RuntimeException("OpenRouter API call failed: " + e.getMessage(), e);
            } catch (ResourceAccessException e) {
                attemptsLeft--;
                if (attemptsLeft > 0) {
                    log.warn("OpenRouter timed out with model {} — {} attempt(s) left, retrying", model, attemptsLeft);
                } else {
                    log.error("OpenRouter timed out with model {} — no retries left", model);
                    throw new TimedOutException(model);
                }
            } catch (RateLimitedException | PaymentRequiredException | TimedOutException e) {
                throw e;
            } catch (Exception e) {
                log.error("OpenRouter API call failed with model {}: {}\nResponse: {}", model, e.getMessage(), response != null ? response.strip() : null, e);
                throw new RuntimeException("OpenRouter API call failed: " + e.getMessage(), e);
            }
        }
    }

    private String stripJsonFences(String text) {
        if (text == null) {
            return null;
        }
        String t = text.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("```\\s*$", "").strip();
        }
        return t;
    }

    private static final class RateLimitedException extends RuntimeException {
        RateLimitedException(String model) {
            super("Rate limited on model: " + model);
        }
    }

    private static final class TimedOutException extends RuntimeException {
        TimedOutException(String model) {
            super("Timed out on model: " + model);
        }
    }
}
