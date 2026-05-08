--liquibase formatted sql

--changeset tubereturns:002-channel-discovery-complete
ALTER TABLE channels ADD COLUMN discovery_complete BOOLEAN NOT NULL DEFAULT FALSE;
