package com.tubereturns.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Slf4j
@RequiredArgsConstructor
@Service
public class OpenRouterModelCatalogService {

    private static final String ROUTER_MODEL = "openrouter/free";

    @Value("${tubereturns.ai.model-refresh.catalog-url}")
    private String catalogUrl;

    @Value("${tubereturns.ai.model-refresh.min-context-tokens}")
    private int minContextTokens;

    @Value("${tubereturns.ai.model-refresh.exclude-keywords}")
    private String excludeKeywords;

    private final AiModelService aiModelService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().requestFactory(timeoutRequestFactory()).build();

    record CatalogModel(String id, double intelligenceIndex, long created) {}

    public int refreshModels() {
        Map<String, CatalogModel> available = fetchEligibleFreeModels();
        if (available.isEmpty()) {
            log.warn("OpenRouter catalog returned no eligible free models — keeping current list");
            return 0;
        }
        List<String> current = aiModelService.getModels();
        List<String> refreshed = new ArrayList<>(current.stream()
                .filter(available::containsKey)
                .filter(id -> !id.equals(ROUTER_MODEL))
                .toList());
        List<String> added = available.values().stream()
                .filter(m -> !refreshed.contains(m.id()) && !m.id().equals(ROUTER_MODEL))
                .sorted(Comparator.comparingDouble(CatalogModel::intelligenceIndex).reversed()
                        .thenComparing(Comparator.comparingLong(CatalogModel::created).reversed()))
                .map(CatalogModel::id)
                .toList();
        refreshed.addAll(added);
        if (available.containsKey(ROUTER_MODEL)) {
            refreshed.add(ROUTER_MODEL);
        }
        List<String> removed = current.stream().filter(id -> !refreshed.contains(id)).toList();
        log.info("OpenRouter model refresh: {} kept, {} added {}, {} removed {}",
                refreshed.size() - added.size(), added.size(), added, removed.size(), removed);
        aiModelService.replaceModels(refreshed);
        return refreshed.size();
    }

    private Map<String, CatalogModel> fetchEligibleFreeModels() {
        List<String> excluded = Arrays.stream(excludeKeywords.split(","))
                .map(String::strip)
                .filter(k -> !k.isBlank())
                .toList();
        String body = restClient.get().uri(catalogUrl).retrieve().body(String.class);
        JsonNode data;
        try {
            data = objectMapper.readTree(body).path("data");
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable OpenRouter model catalog: " + e.getMessage(), e);
        }
        long now = Instant.now().getEpochSecond();
        Map<String, CatalogModel> eligible = new LinkedHashMap<>();
        StreamSupport.stream(data.spliterator(), false)
                .filter(m -> isFree(m.path("id").asText()))
                .filter(m -> m.path("context_length").asLong() >= minContextTokens)
                .filter(m -> outputsTextOnly(m.path("architecture").path("output_modalities")))
                .filter(m -> excluded.stream().noneMatch(k -> m.path("id").asText().contains(k)))
                .filter(m -> !isExpired(m.path("expiration_date"), now))
                .forEach(m -> eligible.put(m.path("id").asText(), new CatalogModel(
                        m.path("id").asText(),
                        m.path("benchmarks").path("artificial_analysis").path("intelligence_index").asDouble(0),
                        m.path("created").asLong())));
        return eligible;
    }

    private static JdkClientHttpRequestFactory timeoutRequestFactory() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    private boolean isFree(String id) {
        return id.endsWith(":free") || id.equals(ROUTER_MODEL);
    }

    private boolean outputsTextOnly(JsonNode outputModalities) {
        return outputModalities.size() == 1 && outputModalities.get(0).asText().equals("text");
    }

    private boolean isExpired(JsonNode expirationDate, long now) {
        if (expirationDate.isNull() || expirationDate.isMissingNode() || expirationDate.asText().isBlank()) {
            return false;
        }
        try {
            return Instant.parse(expirationDate.asText().length() == 10
                    ? expirationDate.asText() + "T00:00:00Z"
                    : expirationDate.asText()).getEpochSecond() <= now;
        } catch (Exception e) {
            return false;
        }
    }
}
