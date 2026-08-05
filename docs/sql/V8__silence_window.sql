CREATE TABLE IF NOT EXISTS silence_window (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_matcher VARCHAR(512),
    level_filter   VARCHAR(16),
    start_at       DATETIME(6) NOT NULL,
    end_at         DATETIME(6) NOT NULL,
    reason         VARCHAR(512),
    created_by     VARCHAR(64),
    created_at     DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
