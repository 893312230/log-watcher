CREATE TABLE IF NOT EXISTS oncall_rotation (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    members_json  TEXT NOT NULL,
    handoff_day   VARCHAR(16) NOT NULL DEFAULT 'MONDAY',
    current_index INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
