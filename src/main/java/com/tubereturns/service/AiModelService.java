package com.tubereturns.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class AiModelService {

    private static final String EXTRACTION_PROMPT = """
        Analyze the following YouTube video transcript and extract stock picks mentioned by the creator.

        Look for:
        1. Stock ticker symbols (e.g., AAPL, TSLA, MSFT)
        2. Company names being discussed as investments
        3. Clear BUY or SELL recommendations

        Return ONLY valid JSON in this exact format with no markdown, no code block, just raw JSON:
        {
          "videoId": "PLACEHOLDER",
          "extractions": [
            {
              "tickerSymbol": "TICKER",
              "companyName": "Company Name",
              "signal": "BUY"
            }
          ]
        }

        Rules:
        - Only include clear investment recommendations
        - signal must be either "BUY" or "SELL"
        - tickerSymbol must be a valid stock ticker (1-5 uppercase letters)
        - If no picks are found, return an empty extractions array

        Transcript:
        """;

    @Value("${tubereturns.ai.provider:mock}")
    private String aiProvider;

    @Value("${tubereturns.ai.api-key:}")
    private String apiKey;

    @Value("${tubereturns.ai.model:}")
    private String configuredModel;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;

    public String extractStockPicks(String transcriptText) {
        return switch (aiProvider) {
            case "anthropic" -> callAnthropic(transcriptText);
            case "openai"    -> callOpenAi(transcriptText);
            case "gemini"    -> callGemini(transcriptText);
            default          -> createMockResponse();
        };
    }

    // ── Anthropic (Claude) ────────────────────────────────────────────────────

    private String callAnthropic(String transcriptText) {
        String model = configuredModel.isBlank() ? "claude-opus-4-5" : configuredModel;
        log.info("Calling Anthropic API with model {}", model);

        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", 1024,
            "messages", List.of(
                Map.of("role", "user", "content", EXTRACTION_PROMPT + transcriptText)
            )
        );

        try {
            String response = restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return stripJsonFences(root.path("content").get(0).path("text").asText());

        } catch (Exception e) {
            log.error("Anthropic API call failed: {}", e.getMessage(), e);
            return createMockResponse();
        }
    }

    // ── OpenAI ────────────────────────────────────────────────────────────────

    private String callOpenAi(String transcriptText) {
        String model = configuredModel.isBlank() ? "gpt-4o" : configuredModel;
        log.info("Calling OpenAI API with model {}", model);

        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "user", "content", EXTRACTION_PROMPT + transcriptText)
            )
        );

        try {
            String response = restClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return stripJsonFences(root.path("choices").get(0).path("message").path("content").asText());

        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage(), e);
            return createMockResponse();
        }
    }

    // ── Gemini ────────────────────────────────────────────────────────────────

    private String callGemini(String transcriptText) {
        String model = configuredModel.isBlank() ? "gemini-2.0-flash" : configuredModel;
        log.info("Calling Gemini API with model {}", model);

        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", EXTRACTION_PROMPT + transcriptText)
                ))
            )
        );

        try {
            String response = restClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}",
                    model, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String text = root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();
            return stripJsonFences(text);

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            return createMockResponse();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    // ── Mock ──────────────────────────────────────────────────────────────────

    private String createMockResponse() {
        return """
            {
              "videoId": "mock_video",
              "extractions": []
            }
            """;
    }
}
