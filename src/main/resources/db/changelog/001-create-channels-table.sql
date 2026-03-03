--liquibase formatted sql

--changeset tubereturns:001
--comment: Create channels table to store YouTube channel information

CREATE TABLE channels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id VARCHAR(255) NOT NULL UNIQUE,
    channel_name VARCHAR(500) NOT NULL,
    description TEXT,
    subscriber_count BIGINT,
    video_count INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true
);

CREATE INDEX idx_channels_channel_id ON channels(channel_id);
CREATE INDEX idx_channels_active ON channels(is_active);