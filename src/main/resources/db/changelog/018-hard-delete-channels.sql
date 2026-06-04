--liquibase formatted sql
--changeset tubereturns:018-hard-delete-channels
ALTER TABLE channel_processing_notifications
    DROP CONSTRAINT IF EXISTS channel_processing_notifications_channel_id_fkey,
    ADD CONSTRAINT channel_processing_notifications_channel_id_fkey
        FOREIGN KEY (channel_id) REFERENCES channels (id) ON DELETE CASCADE;

ALTER TABLE channels DROP COLUMN IF EXISTS deleted_at;
