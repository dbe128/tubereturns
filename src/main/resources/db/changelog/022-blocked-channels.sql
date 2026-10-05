--liquibase formatted sql

--changeset tubereturns:022-blocked-channels
CREATE TABLE blocked_channels (
    id         BIGSERIAL PRIMARY KEY,
    handle     VARCHAR(255) NOT NULL UNIQUE,
    youtube_channel_id VARCHAR(64),
    reason     VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
