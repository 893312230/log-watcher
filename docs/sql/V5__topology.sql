CREATE TABLE IF NOT EXISTS topology_node (
    id       BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(128) NOT NULL,
    type     VARCHAR(32) DEFAULT 'SERVICE',
    host     VARCHAR(256),
    status   VARCHAR(16) DEFAULT 'UNKNOWN',
    metadata TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS topology_edge (
    id        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    type      VARCHAR(32) DEFAULT 'DEPENDS_ON',
    FOREIGN KEY (source_id) REFERENCES topology_node(id),
    FOREIGN KEY (target_id) REFERENCES topology_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
