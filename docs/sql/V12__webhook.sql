CREATE TABLE IF NOT EXISTS webhook_subscription (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    url         VARCHAR(1024) NOT NULL,
    event_types VARCHAR(512) NOT NULL,
    secret      VARCHAR(256),
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    retry_count INT NOT NULL DEFAULT 3,
    created_at  DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS webhook_delivery (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subscription_id BIGINT NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload         TEXT,
    status_code     INT,
    success         BOOLEAN NOT NULL DEFAULT FALSE,
    attempt         INT NOT NULL DEFAULT 1,
    created_at      DATETIME(6) NOT NULL,
    INDEX idx_delivery_sub (subscription_id),
    CONSTRAINT fk_delivery_sub FOREIGN KEY (subscription_id) REFERENCES webhook_subscription (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
