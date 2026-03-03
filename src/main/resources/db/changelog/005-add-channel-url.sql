--liquibase formatted sql

--changeset tubereturns:005
--comment: Add channel_url column to channels table for yt-dlp video discovery

ALTER TABLE channels ADD COLUMN channel_url VARCHAR(1000);
