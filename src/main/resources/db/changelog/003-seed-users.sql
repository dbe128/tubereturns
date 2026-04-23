--liquibase formatted sql

--changeset tubereturns:003-seed-users
INSERT INTO users (first_name, email, password_hash, provider, email_verified, role_id, created_at, updated_at)
VALUES (
    'Balázs',
    'dbe128@gmail.com',
    '$2a$10$kTtjDcQpzW9XawfDcM3Io.v54X4lRBiimXEQntnzBz3RsmbQ3wroa',
    'local',
    TRUE,
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    '2026-04-23 18:14:23.233111',
    '2026-04-23 18:14:45.451901'
);
