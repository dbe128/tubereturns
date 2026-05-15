--liquibase formatted sql

--changeset tubereturns:006-seed-user-dorabali
INSERT INTO users (first_name, email, password_hash, provider, email_verified, role_id, created_at, updated_at)
VALUES (
    'Balazs3',
    'dorabali@freemail.hu',
    '$2a$10$vtei9mvK7qqAfXgiCm8NyuS3NxAs6PAXTomtVRJWc8cfcd.WUeHAe',
    'local',
    TRUE,
    (SELECT id FROM roles WHERE name = 'FREE'),
    '2026-05-14 15:54:44.689937',
    '2026-05-14 15:55:00.433850'
);
