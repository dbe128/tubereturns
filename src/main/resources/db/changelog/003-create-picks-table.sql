--liquibase formatted sql

--changeset tubereturns:003
--comment: Create picks table to store extracted stock picks from videos

CREATE TABLE picks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    ticker_symbol VARCHAR(10) NOT NULL,
    company_name VARCHAR(500),
    signal VARCHAR(10) NOT NULL,
    confidence_score DECIMAL(3,2),
    extraction_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_picks_video FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE
);

CREATE INDEX idx_picks_video_id ON picks(video_id);
CREATE INDEX idx_picks_ticker_symbol ON picks(ticker_symbol);
CREATE INDEX idx_picks_signal ON picks(signal);
CREATE INDEX idx_picks_extraction_timestamp ON picks(extraction_timestamp);