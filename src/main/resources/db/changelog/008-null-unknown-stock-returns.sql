--liquibase formatted sql
--changeset tubereturns:008-null-unknown-stock-returns
UPDATE picks
SET return_1m = NULL, return_1y = NULL, return_3y = NULL,
    alpha_1m  = NULL, alpha_1y  = NULL, alpha_3y  = NULL
FROM stocks
WHERE picks.stock_id = stocks.id
  AND stocks.is_unknown = TRUE
  AND picks.approximated_prices = FALSE;
