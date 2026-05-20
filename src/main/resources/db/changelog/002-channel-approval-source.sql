--liquibase formatted sql

--changeset dbe128:002-channel-approval-source
ALTER TABLE channels ADD COLUMN approval_source VARCHAR(20);
