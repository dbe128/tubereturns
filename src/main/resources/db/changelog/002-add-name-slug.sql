--liquibase formatted sql

--changeset tubereturns:002-add-name-slug
ALTER TABLE channels ADD COLUMN name_slug VARCHAR(255);

--changeset tubereturns:002-populate-name-slug-pg dbms:postgresql
UPDATE channels SET name_slug = REGEXP_REPLACE(LOWER(REGEXP_REPLACE(channel_name, '[^a-z0-9]+', '-', 'g')), '^-|-$', '', 'g') || '-returns';

--changeset tubereturns:002-populate-name-slug-h2 dbms:h2
UPDATE channels SET name_slug = REGEXP_REPLACE(LOWER(REGEXP_REPLACE(channel_name, '[^a-z0-9]+', '-')), '^-|-$', '') || '-returns';

--changeset tubereturns:002-name-slug-not-null
ALTER TABLE channels ALTER COLUMN name_slug SET NOT NULL;

--changeset tubereturns:002-name-slug-unique
CREATE UNIQUE INDEX uq_channels_name_slug ON channels (name_slug);
