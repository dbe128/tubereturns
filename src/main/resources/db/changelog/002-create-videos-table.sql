--liquibase formatted sql

--changeset tubereturns:002
--comment: Create videos table to store YouTube video information

CREATE TABLE videos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id VARCHAR(255) NOT NULL UNIQUE,
    channel_id BIGINT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    published_at TIMESTAMP NOT NULL,
    duration_seconds INTEGER,
    view_count BIGINT,
    like_count BIGINT,
    transcript_text TEXT,
    transcript_status VARCHAR(50) DEFAULT 'PENDING',
    processing_status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_videos_channel FOREIGN KEY (channel_id) REFERENCES channels(id) ON DELETE CASCADE
);

CREATE INDEX idx_videos_video_id ON videos(video_id);
CREATE INDEX idx_videos_channel_id ON videos(channel_id);
CREATE INDEX idx_videos_published_at ON videos(published_at);
CREATE INDEX idx_videos_transcript_status ON videos(transcript_status);
CREATE INDEX idx_videos_processing_status ON videos(processing_status);