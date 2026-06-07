--liquibase formatted sql
--changeset tubereturns:019-ibkr-banner-flag

INSERT INTO feature_flags (key, enabled, description) VALUES
    ('ibkr_banner', false, 'Show Interactive Brokers affiliate banner on the leaderboard');
