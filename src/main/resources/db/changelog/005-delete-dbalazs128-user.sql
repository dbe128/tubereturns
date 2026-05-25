--liquibase formatted sql

--changeset tubereturns:005-delete-dbalazs128-user
DELETE FROM users WHERE email = 'dbalazs128@gmail.com';
