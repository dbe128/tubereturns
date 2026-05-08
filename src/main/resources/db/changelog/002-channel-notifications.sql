--liquibase formatted sql

--changeset tubereturns:003
CREATE TABLE channel_processing_notifications (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES channels(id),
    user_email VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_cpn_channel_unsent ON channel_processing_notifications(channel_id) WHERE sent_at IS NULL;
