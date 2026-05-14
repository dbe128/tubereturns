-- liquibase formatted sql

-- changeset tubereturns:005-channel-suggestions
CREATE TABLE channel_suggestions (
    handle                 VARCHAR(255) PRIMARY KEY,
    channel_name           VARCHAR(500) NOT NULL,
    channel_url            TEXT,
    thumbnail_url          TEXT,
    thumbnail_data         BYTEA,
    thumbnail_content_type VARCHAR(50),
    description            TEXT,
    subscriber_count       BIGINT,
    first_suggested_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE channel_suggestion_subscribers (
    id                 BIGSERIAL PRIMARY KEY,
    handle             VARCHAR(255) NOT NULL REFERENCES channel_suggestions(handle),
    user_id            BIGINT NOT NULL REFERENCES users(id),
    notify_on_complete BOOLEAN NOT NULL DEFAULT FALSE,
    subscribed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (handle, user_id)
);
