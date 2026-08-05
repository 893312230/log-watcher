CREATE TABLE IF NOT EXISTS slo (
    id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(128) NOT NULL,
    service_name       VARCHAR(128) NOT NULL,
    metric_name        VARCHAR(128),
    target_pct         DOUBLE NOT NULL,
    window_days        INT NOT NULL DEFAULT 30,
    error_budget_pct   DOUBLE NOT NULL DEFAULT 0.1,
    enabled            BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
