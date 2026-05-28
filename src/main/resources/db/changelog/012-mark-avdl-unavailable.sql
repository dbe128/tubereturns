--liquibase formatted sql

--changeset tubereturns:012-mark-avdl-unavailable
UPDATE stocks
SET corporate_action = 'UNAVAILABLE'
WHERE ticker_symbol = 'AVDL';

DELETE FROM stock_prices
WHERE stock_id = (SELECT id FROM stocks WHERE ticker_symbol = 'AVDL');

UPDATE picks
SET return_1m           = NULL,
    return_1y           = NULL,
    return_3y           = NULL,
    alpha_1m            = NULL,
    alpha_1y            = NULL,
    alpha_3y            = NULL,
    approximated_prices = FALSE
WHERE stock_id = (SELECT id FROM stocks WHERE ticker_symbol = 'AVDL');
