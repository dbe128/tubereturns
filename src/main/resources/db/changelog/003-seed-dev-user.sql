--liquibase formatted sql

--changeset tubereturns:003-seed-dev-user
INSERT INTO users (first_name, email, password_hash, provider, email_verified, role_id, created_at, updated_at)
VALUES (
    'Balázs2',
    'dbalazs128@gmail.com',
    '$2a$10$NRq54lV/dZ2jDp4I9/r82eLR5Ry4jYLCjXZrzd7GPslBQBZz080gi',
    'local',
    TRUE,
    (SELECT id FROM roles WHERE name = 'FREE'),
    '2026-05-12 09:13:30.579577',
    '2026-05-12 09:13:45.368842'
);
