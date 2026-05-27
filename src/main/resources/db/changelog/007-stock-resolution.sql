--liquibase formatted sql

--changeset tubereturns:007-stock-resolution
ALTER TABLE stocks ADD COLUMN resolution_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE stocks ADD COLUMN corporate_action VARCHAR(50);
ALTER TABLE stock_prices ADD COLUMN approximated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE picks ADD COLUMN approximated_prices BOOLEAN NOT NULL DEFAULT FALSE;
