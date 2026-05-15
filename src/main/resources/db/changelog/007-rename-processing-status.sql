--liquibase formatted sql

--changeset tubereturns:007-rename-processing-status
ALTER TABLE videos RENAME COLUMN processing_status TO extraction_status;
DROP INDEX IF EXISTS idx_videos_processing_status;
CREATE INDEX idx_videos_extraction_status ON videos (extraction_status);
