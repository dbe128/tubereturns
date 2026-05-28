--liquibase formatted sql
--changeset tubereturns:014-clear-ttsh-returns

UPDATE picks
SET return_1m        = NULL,
    return_1y        = NULL,
    return_3y        = NULL,
    alpha_1m         = NULL,
    alpha_1y         = NULL,
    alpha_3y         = NULL,
    approximated_prices = FALSE
WHERE stock_id = (SELECT id FROM stocks WHERE ticker_symbol = 'TTSH');
