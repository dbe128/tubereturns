--liquibase formatted sql

--changeset tubereturns:001
--comment: Create initial schema — channels, videos, picks

CREATE TABLE channels (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id  VARCHAR(255) NOT NULL UNIQUE,
    channel_name VARCHAR(500) NOT NULL,
    description TEXT,
    subscriber_count BIGINT,
    video_count INTEGER,
    is_active   BOOLEAN DEFAULT true,
    channel_url VARCHAR(1000),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_channels_channel_id ON channels(channel_id);
CREATE INDEX idx_channels_active ON channels(is_active);

CREATE TABLE videos (
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
);

CREATE INDEX idx_videos_video_id ON videos(video_id);
CREATE INDEX idx_videos_channel_id ON videos(channel_id);
CREATE INDEX idx_videos_published_at ON videos(published_at);
CREATE INDEX idx_videos_transcript_status ON videos(transcript_status);
CREATE INDEX idx_videos_processing_status ON videos(processing_status);

CREATE TABLE picks (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id     BIGINT NOT NULL,
    ticker_symbol VARCHAR(10) NOT NULL,
    company_name  VARCHAR(500),
    signal        VARCHAR(10) NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_picks_video FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE
);

CREATE INDEX idx_picks_video_id ON picks(video_id);
CREATE INDEX idx_picks_ticker_symbol ON picks(ticker_symbol);
CREATE INDEX idx_picks_signal ON picks(signal);
