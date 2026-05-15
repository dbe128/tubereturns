--liquibase formatted sql

--changeset tubereturns:008-rename-extraction-status-values
UPDATE videos SET extraction_status = 'EXTRACTING' WHERE extraction_status = 'PROCESSING';
UPDATE videos SET extraction_status = 'EXTRACTED' WHERE extraction_status = 'COMPLETED';
