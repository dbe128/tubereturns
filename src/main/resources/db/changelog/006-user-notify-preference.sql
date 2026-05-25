--liquibase formatted sql

--changeset tubereturns:006-user-notify-preference
ALTER TABLE users ADD COLUMN notify_on_channel_processed BOOLEAN NOT NULL DEFAULT TRUE;
