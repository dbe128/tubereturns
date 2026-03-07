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

    private static final String MODEL = "stepfun/step-3.5-flash";

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
        - You MUST include EVERY investment recommendation mentioned in the transcript — do not skip or summarise any
        - signal must be either "BUY" or "SELL"
        - tickerSymbol must be a valid stock ticker (1-5 uppercase letters)
        - If no picks are found, return an empty extractions array

        Transcript:
        """;

    @Value("${tubereturns.ai.provider:mock}")
    private String aiProvider;

    @Value("${tubereturns.ai.api-key:}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;

    public String extractStockPicks(String transcriptText) {
        return switch (aiProvider) {
            case "openrouter" -> callOpenRouter(transcriptText);
            default           -> createMockResponse();
        };
    }

    private String callOpenRouter(String transcriptText) {
        log.info("Calling OpenRouter API with model {}", MODEL);

        Map<String, Object> body = Map.of(
            "model", MODEL,
            "messages", List.of(
                Map.of("role", "user", "content", EXTRACTION_PROMPT + transcriptText)
            )
        );

        try {
            String response = restClient.post()
                .uri("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String text = root.path("choices").get(0).path("message").path("content").asText();
            return stripJsonFences(text);

        } catch (Exception e) {
            log.error("OpenRouter API call failed: {}", e.getMessage(), e);
            return createMockResponse();
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
              "extractions": []
            }
            """;
    }
}
