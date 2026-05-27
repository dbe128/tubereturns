--liquibase formatted sql
--changeset tubereturns:009-rename-resolution-attempts
ALTER TABLE stocks RENAME COLUMN resolution_attempts TO failed_resolution_attempts;
