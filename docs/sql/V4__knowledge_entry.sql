-- ============================================================================
-- V4__knowledge_entry.sql — 阶段六运维知识库表
-- 执行方式：mysql -u<user> -p <database> < docs/sql/V4__knowledge_entry.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS knowledge_entry (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title            VARCHAR(512) NOT NULL COMMENT '标题',
    error_pattern    VARCHAR(1024) NULL COMMENT '错误特征',
    root_cause       TEXT         NULL COMMENT '根因分析',
    suggestion       TEXT         NULL COMMENT '修复建议',
    action_items     TEXT         NULL COMMENT '逗号分隔处置意见列表',
    category         VARCHAR(64)  NULL COMMENT '分类',
    tags             VARCHAR(512) NULL COMMENT '逗号分隔标签',
    source           VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源',
    source_alert_id  BIGINT       NULL COMMENT '关联告警 id',
    server_config_id BIGINT       NULL COMMENT '关联服务器配置 id',
    created_by       VARCHAR(128) NULL COMMENT '创建人',
    created_at       DATETIME(6)  NOT NULL COMMENT '创建时间',
    updated_at       DATETIME(6)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_kb_category (category),
    INDEX idx_kb_source (source),
    INDEX idx_kb_server (server_config_id),
    INDEX idx_kb_created (created_at),
    FULLTEXT idx_kb_fulltext (title, root_cause, suggestion)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
