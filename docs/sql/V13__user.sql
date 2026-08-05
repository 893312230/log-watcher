CREATE TABLE IF NOT EXISTS sys_user (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64) NOT NULL,
    password   VARCHAR(128) NOT NULL,
    role       VARCHAR(32) NOT NULL DEFAULT 'VIEWER',
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
