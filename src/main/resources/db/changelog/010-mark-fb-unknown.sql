--liquibase formatted sql
--changeset tubereturns:010-mark-fb-unknown
UPDATE stocks SET is_unknown = TRUE WHERE ticker_symbol = 'FB';
