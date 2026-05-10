package com.tubereturns.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class AiModelService {

    private static final String EXTRACTION_PROMPT_TEMPLATE = """
        Analyze the following YouTube video transcript and extract stock picks mentioned by the creator.

        Video title: %s

        Look for:
        1. Stock ticker symbols (e.g., AAPL, TSLA, MSFT)
        2. Company names being discussed as investments
        3. Explicit BUY or SELL recommendations
        4. Implicit signals based on the creator's own analysis or opinion — see signal rules below

        Output ONLY the raw JSON object below — no explanation, no markdown, no code fences, no text before or after:
        {
          "videoId": "PLACEHOLDER",
          "externalPositions": false,
          "extractions": [
            {
              "tickerSymbol": "TICKER",
              "companyName": "Company Name",
              "signal": "BUY"
            }
          ]
        }

        Rules:
        - You MUST include EVERY investment recommendation mentioned in the transcript — do not skip or summarise any
        - signal must be either "BUY" or "SELL"
        - tickerSymbol must be a valid stock ticker (1-5 uppercase letters)
        - If no picks are found, return an empty extractions array
        - Set externalPositions to true if the transcript only presents positions or trades made by someone else (another person, an AI agent, a portfolio manager, etc.) rather than the video creator's own picks — the creator is merely reporting or reviewing them, not recommending them personally
        - Implicit BUY signals: the creator expresses that a stock is undervalued, attractively priced, a good investment, a compelling opportunity, has strong upside, or otherwise indicates bullish conviction based on their own analysis — treat this as BUY
        - Implicit SELL signals: the creator expresses that a stock is overvalued, too expensive, a poor investment, has limited upside or significant downside risk, or otherwise indicates bearish conviction based on their own analysis — treat this as SELL
        - Only apply implicit signals when the creator is expressing their own view, not when merely describing the stock or reporting others' opinions

        Transcript:
        """;

    @Value("${tubereturns.ai.provider:mock}")
    private String aiProvider;

    @Value("${tubereturns.ai.api-key:}")
    private String apiKey;

    @Value("${tubereturns.ai.models-file:llm-models.txt}")
    private String modelsFilePath;

    @Value("${tubereturns.ai.model-reset-minutes:60}")
    private int modelResetMinutes;

    private List<String> models;
    private final AtomicInteger currentModelIndex = new AtomicInteger(0);
    private volatile Instant lastModel0AttemptAt = null;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void loadModels() {
        Path path = Path.of(modelsFilePath);
        if (Files.exists(path)) {
            try {
                models = Files.readAllLines(path).stream()
                        .map(String::strip)
                        .filter(l -> !l.isBlank() && !l.startsWith("#"))
                        .collect(Collectors.toList());
                log.info("Loaded {} AI model(s) from {}: first={}", models.size(), modelsFilePath, models.isEmpty() ? "none" : models.getFirst());
            } catch (Exception e) {
                log.error("Failed to read models file {}: {}", modelsFilePath, e.getMessage());
                models = List.of("openrouter/owl-alpha");
            }
        } else {
            log.warn("Models file not found at {} — using default model", modelsFilePath);
            models = List.of("openrouter/owl-alpha");
        }
    }

    public record ExtractionResult(String content, String model) {}

    public record AiModelStatus(int currentIndex, String currentModel, Instant model0ResetAt) {}

    public AiModelStatus getStatus() {
        int idx = currentModelIndex.get();
        String model = (models != null && !models.isEmpty()) ? models.get(idx % models.size()) : "unknown";
        Instant resetAt = lastModel0AttemptAt != null && idx > 0
                ? lastModel0AttemptAt.plusSeconds(modelResetMinutes * 60L)
                : null;
        return new AiModelStatus(idx, model, resetAt);
    }

    public ExtractionResult extractStockPicks(String videoId, String videoTitle, String transcriptText) {
        return switch (aiProvider) {
            case "openrouter" -> callOpenRouter(videoId, videoTitle, transcriptText);
            default           -> new ExtractionResult(createMockResponse(), "mock");
        };
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

        int startIndex = currentModelIndex.get();
        int size = models.size();

        for (int attempt = 0; attempt < size; attempt++) {
            int idx = (startIndex + attempt) % size;
            String model = models.get(idx);
            if (idx == 0) {
                lastModel0AttemptAt = Instant.now();
            }
            try {
                return callWithModel(model, videoId, videoTitle, transcriptText);
            } catch (RateLimitedException e) {
                int nextIdx = (idx + 1) % size;
                currentModelIndex.set(nextIdx);
                if (attempt < size - 1) {
                    log.warn("Rate limited on model {} — switching to {}", model, models.get(nextIdx));
                } else {
                    log.error("Rate limited on model {} — all {} models exhausted", model, size);
                }
            }
        }
        throw new RuntimeException("All " + size + " AI models exhausted due to rate limiting for video " + videoId);
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

        String response = null;
        try {
            response = restClient.post()
                .uri("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String actualModel = root.path("model").asText(model);
            log.info("OpenRouter used model: {}", actualModel);
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
            log.error("OpenRouter API call failed with model {}: {}\nResponse: {}", model, e.getMessage(), response, e);
            throw new RuntimeException("OpenRouter API call failed: " + e.getMessage(), e);
        } catch (RateLimitedException | PaymentRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenRouter API call failed with model {}: {}\nResponse: {}", model, e.getMessage(), response, e);
            throw new RuntimeException("OpenRouter API call failed: " + e.getMessage(), e);
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

    private String createMockResponse() {
        return """
            {
              "videoId": "mock_video",
              "externalPositions": false,
              "extractions": []
            }
            """;
    }

    private static final class RateLimitedException extends RuntimeException {
        RateLimitedException(String model) {
            super("Rate limited on model: " + model);
        }
    }
}
