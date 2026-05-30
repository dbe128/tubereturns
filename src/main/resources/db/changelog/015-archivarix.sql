--liquibase formatted sql

--changeset tubereturns:015-archivarix
ALTER TABLE channels ADD COLUMN archivarix_deleted_count INT;
ALTER TABLE channels ADD COLUMN archivarix_checked_at TIMESTAMP;

CREATE TABLE archivarix_deleted_videos (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES channels(id),
    youtube_video_id VARCHAR(20) NOT NULL,
    title TEXT,
    upload_date DATE,
    archivarix_status VARCHAR(30) NOT NULL,
    discovered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (channel_id, youtube_video_id)
);

CREATE INDEX idx_archivarix_deleted_videos_channel_id ON archivarix_deleted_videos(channel_id);
