--liquibase formatted sql
--changeset tubereturns:004-stock-unknown

ALTER TABLE stocks ADD COLUMN is_unknown BOOLEAN NOT NULL DEFAULT false;
