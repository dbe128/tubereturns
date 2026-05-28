--liquibase formatted sql

--changeset tubereturns:011-mark-arax-bankrupt
UPDATE stocks
SET corporate_action = 'BANKRUPT'
WHERE ticker_symbol = 'ARAX';

UPDATE picks
SET return_1m           = NULL,
    return_1y           = NULL,
    return_3y           = NULL,
    alpha_1m            = NULL,
    alpha_1y            = NULL,
    alpha_3y            = NULL,
    approximated_prices = FALSE
WHERE stock_id = (SELECT id FROM stocks WHERE ticker_symbol = 'ARAX');
