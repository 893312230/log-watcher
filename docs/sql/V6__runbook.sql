CREATE TABLE IF NOT EXISTS runbook (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(512),
    trigger_keyword     VARCHAR(128),
    safety_level        INT NOT NULL DEFAULT 1,
    rollback_steps_json TEXT,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS runbook_step (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    runbook_id     BIGINT NOT NULL,
    seq            INT NOT NULL,
    step_type      VARCHAR(16) NOT NULL DEFAULT 'LLM',
    config_json    TEXT,
    condition_expr VARCHAR(512),
    FOREIGN KEY (runbook_id) REFERENCES runbook(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS runbook_execution (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    runbook_id  BIGINT NOT NULL,
    started_at  DATETIME(6) NOT NULL,
    finished_at DATETIME(6),
    status      VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    FOREIGN KEY (runbook_id) REFERENCES runbook(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS runbook_step_result (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    seq          INT NOT NULL,
    command      TEXT,
    status       VARCHAR(16) NOT NULL,
    output       TEXT,
    FOREIGN KEY (execution_id) REFERENCES runbook_execution(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
