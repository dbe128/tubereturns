--liquibase formatted sql

--changeset tubereturns:013-blacklisted-tickers
CREATE TABLE blacklisted_tickers (
    id         BIGSERIAL PRIMARY KEY,
    ticker_symbol VARCHAR(20) NOT NULL UNIQUE,
    reason     VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO blacklisted_tickers (ticker_symbol, reason) VALUES ('TICKER', 'Default placeholder');
