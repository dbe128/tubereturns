--liquibase formatted sql

--changeset tubereturns:005-stock-currency-and-ticker-length
ALTER TABLE stocks ALTER COLUMN ticker_symbol TYPE VARCHAR(20);
ALTER TABLE stocks ADD COLUMN currency VARCHAR(3);
