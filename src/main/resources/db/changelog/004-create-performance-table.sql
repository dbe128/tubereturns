--liquibase formatted sql

--changeset tubereturns:004
--comment: Create performance table to store calculated returns for stock picks

CREATE TABLE performance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pick_id BIGINT NOT NULL UNIQUE,
    start_price DECIMAL(12,4),
    current_price DECIMAL(12,4),
    return_1d DECIMAL(8,4),
    return_7d DECIMAL(8,4),
    return_30d DECIMAL(8,4),
    return_90d DECIMAL(8,4),
    return_1y DECIMAL(8,4),
    return_ytd DECIMAL(8,4),
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_performance_pick FOREIGN KEY (pick_id) REFERENCES picks(id) ON DELETE CASCADE
);

CREATE INDEX idx_performance_pick_id ON performance(pick_id);
CREATE INDEX idx_performance_last_updated ON performance(last_updated);