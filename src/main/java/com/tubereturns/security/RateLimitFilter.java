package com.tubereturns.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.tubereturns.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String TIER_AUTH = "auth";
    private static final String TIER_STANDARD = "standard";
    private static final String TIER_AUTHENTICATED = "authenticated";

    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;
    private final LoadingCache<String, Bucket> buckets;

    public RateLimitFilter(RateLimitProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(this::buildBucket);
    }

    private Bucket buildBucket(String key) {
        RateLimitProperties.Tier tier;
        if (key.endsWith(":" + TIER_AUTH)) {
            tier = props.getAuth();
        } else if (key.endsWith(":" + TIER_AUTHENTICATED)) {
            tier = props.getAuthenticated();
        } else {
            tier = props.getStandard();
        }
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(tier.getCapacity())
                        .refillGreedy(tier.getCapacity(), Duration.ofSeconds(tier.getRefillSeconds()))
                        .build())
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!props.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/api/admin/") || path.startsWith("/actuator/") || path.equals("/api/sitemap.xml")) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.isAuthenticated();

        String clientKey;
        String tierName;
        if (isAuthenticated) {
            clientKey = "user:" + auth.getName();
            tierName = TIER_AUTHENTICATED;
        } else {
            clientKey = getClientIp(request);
            tierName = path.startsWith("/api/auth/") ? TIER_AUTH : TIER_STANDARD;
        }

        Bucket bucket = buckets.get(clientKey + ":" + tierName);
        if (bucket == null || bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitProperties.Tier tier = resolveTier(tierName);
        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(tier.getRefillSeconds()));
        objectMapper.writeValue(response.getWriter(),
                Map.of("error", "Too many requests", "retryAfterSeconds", tier.getRefillSeconds()));
        log.warn("Rate limit exceeded for {} on {}", clientKey, path);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private RateLimitProperties.Tier resolveTier(String tierName) {
        return switch (tierName) {
            case TIER_AUTH -> props.getAuth();
            case TIER_AUTHENTICATED -> props.getAuthenticated();
            default -> props.getStandard();
        };
    }
}
