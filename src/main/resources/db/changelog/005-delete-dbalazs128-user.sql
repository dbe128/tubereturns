--liquibase formatted sql

--changeset tubereturns:005-delete-dbalazs128-user
DELETE FROM channel_processing_notifications WHERE user_id = (SELECT id FROM users WHERE email = 'dbalazs128@gmail.com');
DELETE FROM channel_suggestion_subscribers WHERE user_id = (SELECT id FROM users WHERE email = 'dbalazs128@gmail.com');
DELETE FROM users WHERE email = 'dbalazs128@gmail.com';
