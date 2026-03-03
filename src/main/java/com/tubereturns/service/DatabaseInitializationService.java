package com.tubereturns.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class DatabaseInitializationService {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // Run before channel initialization
    public void initializeDatabase() {
        log.info("Initializing database schema");

        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS channels (
                    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                    channel_id   VARCHAR(255) NOT NULL UNIQUE,
                    channel_name VARCHAR(500) NOT NULL,
                    description  TEXT,
                    subscriber_count BIGINT,
                    video_count  INTEGER,
                    is_active    BOOLEAN DEFAULT true,
                    channel_url  VARCHAR(1000),
                    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS videos (
                    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                    video_id         VARCHAR(255) NOT NULL UNIQUE,
                    channel_id       BIGINT NOT NULL,
                    title            TEXT NOT NULL,
                    description      TEXT,
                    published_at     TIMESTAMP NOT NULL,
                    duration_seconds INTEGER,
                    view_count       BIGINT,
                    like_count       BIGINT,
                    transcript_text  TEXT,
                    transcript_status  VARCHAR(50) DEFAULT 'PENDING',
                    processing_status  VARCHAR(50) DEFAULT 'PENDING',
                    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_videos_channel FOREIGN KEY (channel_id) REFERENCES channels(id) ON DELETE CASCADE
                )
                """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS picks (
                    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                    video_id      BIGINT NOT NULL,
                    ticker_symbol VARCHAR(10) NOT NULL,
                    company_name  VARCHAR(500),
                    signal        VARCHAR(10) NOT NULL,
                    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_picks_video FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE
                )
                """);

            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_channels_channel_id ON channels(channel_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_videos_video_id ON videos(video_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_videos_channel_id ON videos(channel_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_picks_video_id ON picks(video_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_picks_ticker_symbol ON picks(ticker_symbol)");

            log.info("Database schema initialization completed successfully");

        } catch (Exception e) {
            log.error("Error initializing database schema: {}", e.getMessage(), e);
        }
    }
}
