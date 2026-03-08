--liquibase formatted sql

--changeset tubereturns:002
--comment: Add last_processed_at to channels to track ingestion resume point

ALTER TABLE channels ADD COLUMN last_processed_at TIMESTAMP;
