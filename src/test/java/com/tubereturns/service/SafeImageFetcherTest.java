package com.tubereturns.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SafeImageFetcherTest {

    private final SafeImageFetcher fetcher = new SafeImageFetcher();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fetcher, "allowedHosts",
                List.of("yt3.ggpht.com", "yt3.googleusercontent.com", "i.ytimg.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///proc/self/environ",
            "file:///etc/passwd",
            "http://yt3.ggpht.com/avatar.jpg",
            "https://169.254.169.254/latest/meta-data/",
            "https://localhost:8080/actuator/env",
            "https://127.0.0.1/",
            "https://example.com/image.jpg",
            "https://yt3.ggpht.com.evil.example/avatar.jpg",
            "https://evil.example/yt3.ggpht.com/avatar.jpg",
            "https://yt3.ggpht.com@evil.example/avatar.jpg",
            "ftp://yt3.ggpht.com/avatar.jpg",
            "not a url",
            ""
    })
    void rejectsUrlsOutsideTheAllowlist(String url) {
        assertThat(fetcher.fetch(url)).isNull();
    }
}
