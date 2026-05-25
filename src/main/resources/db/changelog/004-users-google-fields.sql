--liquibase formatted sql

--changeset tubereturns:004-users-google-fields
ALTER TABLE users ADD COLUMN last_name VARCHAR(255);
ALTER TABLE users ADD COLUMN profile_picture_url VARCHAR(512);
