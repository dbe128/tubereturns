--liquibase formatted sql
--changeset tubereturns:021-lightyear-banner-flag

INSERT INTO feature_flags (key, enabled, description) VALUES
    ('lightyear_banner', false, 'Show Lightyear affiliate banner at the bottom of the channel page');
