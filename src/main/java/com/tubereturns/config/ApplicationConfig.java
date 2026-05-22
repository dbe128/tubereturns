package com.tubereturns.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.concurrent.TimeUnit;

@EnableCaching
@Configuration
public class ApplicationConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache allChannels = new CaffeineCache("allChannels",
                Caffeine.newBuilder()
                        .expireAfterWrite(4, TimeUnit.HOURS)
                        .maximumSize(1)
                        .recordStats()
                        .build());
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(allChannels));
        return manager;
    }
}
