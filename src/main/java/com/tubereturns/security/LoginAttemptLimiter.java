package com.tubereturns.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.tubereturns.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class LoginAttemptLimiter {

    private final RateLimitProperties props;
    private final LoadingCache<String, Bucket> failuresByAccount;

    public LoginAttemptLimiter(RateLimitProperties props) {
        this.props = props;
        this.failuresByAccount = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(100_000)
                .build(key -> newBucket());
    }

    public boolean isBlocked(String email) {
        return props.isEnabled() && failuresByAccount.get(key(email)).getAvailableTokens() <= 0;
    }

    public void recordFailure(String email) {
        if (props.isEnabled()) {
            failuresByAccount.get(key(email)).tryConsume(1);
        }
    }

    private Bucket newBucket() {
        RateLimitProperties.Tier tier = props.getLoginAccount();
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(tier.getCapacity())
                        .refillGreedy(tier.getCapacity(), Duration.ofSeconds(tier.getRefillSeconds()))
                        .build())
                .build();
    }

    private String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
