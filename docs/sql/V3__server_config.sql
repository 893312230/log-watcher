-- ============================================================================
-- V3__server_config.sql — 阶段六服务器配置管理表
-- 执行方式：mysql -u<user> -p <database> < docs/sql/V3__server_config.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS server_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(128) NOT NULL COMMENT '服务名称',
    host        VARCHAR(256) NULL COMMENT '服务器地址',
    deploy_path VARCHAR(512) NULL COMMENT '应用部署路径',
    code_repo   VARCHAR(512) NULL COMMENT '代码库路径',
    log_path    VARCHAR(512) NULL COMMENT '日志文件路径',
    description TEXT         NULL COMMENT '描述',
    tags        VARCHAR(512) NULL COMMENT '逗号分隔标签',
    created_at  DATETIME(6)  NOT NULL COMMENT '创建时间',
    updated_at  DATETIME(6)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_sc_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
