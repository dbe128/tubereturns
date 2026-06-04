package com.tubereturns.controller;

import com.tubereturns.dto.FeatureFlagDto;
import com.tubereturns.model.FeatureFlag;
import com.tubereturns.repository.FeatureFlagRepository;
import com.tubereturns.repository.PickRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlagRepository featureFlagRepository;
    private final PickRepository pickRepository;

    @GetMapping("/api/feature-flags")
    @Operation(summary = "Get all feature flags")
    public List<FeatureFlagDto> getAll() {
        return featureFlagRepository.findAll().stream()
                .map(f -> new FeatureFlagDto(f.getKey(), f.isEnabled(), f.getDescription()))
                .toList();
    }

    @PutMapping("/api/admin/feature-flags/{key}")
    @Operation(summary = "Set a feature flag enabled/disabled")
    public ResponseEntity<FeatureFlagDto> setFlag(@PathVariable String key, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return featureFlagRepository.findById(key)
                .map(flag -> {
                    flag.setEnabled(enabled);
                    featureFlagRepository.save(flag);
                    return ResponseEntity.ok(new FeatureFlagDto(flag.getKey(), flag.isEnabled(), flag.getDescription()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/trending-picks")
    @Operation(summary = "Top 5 most picked stocks from videos published in the last 5 days")
    public List<Map<String, Object>> getTrendingPicks() {
        return pickRepository.findTrendingPicks(Instant.now().minus(24, ChronoUnit.HOURS), PageRequest.of(0, 5))
                .stream()
                .map(row -> Map.<String, Object>of(
                        "tickerSymbol", row[0],
                        "companyName", row[1] != null ? row[1] : "",
                        "pickCount", row[2]
                ))
                .toList();
    }
}
