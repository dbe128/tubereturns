package com.tubereturns.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class AiModelService {

    private static final String MODEL = "openrouter/owl-alpha";

    private static final String EXTRACTION_PROMPT_TEMPLATE = """
        Analyze the following YouTube video transcript and extract stock picks mentioned by the creator.

        Video title: %s

        Look for:
        1. Stock ticker symbols (e.g., AAPL, TSLA, MSFT)
        2. Company names being discussed as investments
        3. Clear BUY or SELL recommendations

        Return ONLY valid JSON in this exact format with no markdown, no code block, just raw JSON:
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

        Transcript:
        """;

    @Value("${tubereturns.ai.provider:mock}")
    private String aiProvider;

    @Value("${tubereturns.ai.api-key:}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;

    public record ExtractionResult(String content, String model) {}

    public ExtractionResult extractStockPicks(String videoTitle, String transcriptText) {
        return switch (aiProvider) {
            case "openrouter" -> callOpenRouter(videoTitle, transcriptText);
            default           -> new ExtractionResult(createMockResponse(), "mock");
        };
    }

    private ExtractionResult callOpenRouter(String videoTitle, String transcriptText) {
        log.info("Calling OpenRouter API with model {}", MODEL);

        String prompt = EXTRACTION_PROMPT_TEMPLATE.formatted(videoTitle) + transcriptText;
        Map<String, Object> body = Map.of(
            "model", MODEL,
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
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
            String actualModel = root.path("model").asText(MODEL);
            log.info("OpenRouter used model: {}", actualModel);
            String text = root.path("choices").get(0).path("message").path("content").asText();
            return new ExtractionResult(stripJsonFences(text), actualModel);

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
                log.error("OpenRouter returned 402 Payment Required — insufficient credits");
                throw new PaymentRequiredException("OpenRouter API returned 402: insufficient credits");
            }
            log.error("OpenRouter API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("OpenRouter API call failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("OpenRouter API call failed: {}", e.getMessage(), e);
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
}
