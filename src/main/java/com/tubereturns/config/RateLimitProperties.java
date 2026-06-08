package com.tubereturns.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tubereturns.rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    private boolean enabled;
    private Tier auth = new Tier();
    private Tier standard = new Tier();
    private Tier authenticated = new Tier();

    @Getter
    @Setter
    public static class Tier {
        private int capacity;
        private int refillSeconds;
    }
}
