--liquibase formatted sql
--changeset tubereturns:016-youtube-channel-id
ALTER TABLE channels ADD COLUMN youtube_channel_id VARCHAR(24);
