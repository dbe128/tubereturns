--liquibase formatted sql

--changeset tubereturns:023-user-token-version
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
