--liquibase formatted sql

--changeset dbe128:004-stock-reviewed
ALTER TABLE stocks ADD COLUMN reviewed BOOLEAN NOT NULL DEFAULT FALSE;
