--liquibase formatted sql
--changeset tubereturns:017-feature-flags
CREATE TABLE feature_flags (
    key VARCHAR(100) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(500)
);

INSERT INTO feature_flags (key, enabled, description) VALUES
    ('trending_stocks', false, 'Show trending stock picks on the leaderboard');
