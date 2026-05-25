--liquibase formatted sql

--changeset tubereturns:003-fix-name-slug-pg dbms:postgresql
UPDATE channels SET name_slug = REGEXP_REPLACE(REGEXP_REPLACE(LOWER(channel_name), '[^a-z0-9]+', '-', 'g'), '^-|-$', '', 'g') || '-returns';
