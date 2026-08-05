-- ============================================================================
-- V1__alert_record.sql — 阶段五 logwatch 告警记录表
--
-- 背景：生产环境 spring.jpa.hibernate.ddl-auto=validate，新表必须随发布手工执行。
-- 对应实体：smart-ops-infrastructure persistence/alert/AlertRecordEntity.java
-- 执行方式：mysql -u<user> -p <database> < docs/sql/V1__alert_record.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS alert_record (
    -- 主键（自增）
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    -- 事件指纹（L0 去重键，SHA-256 十六进制 64 字符）
    fingerprint   VARCHAR(64)  NOT NULL COMMENT '事件指纹',
    -- 日志来源（文件路径或 jar 包路径）
    source        VARCHAR(512) NOT NULL COMMENT '日志来源',
    -- 告警级别（ERROR/WARN/INFO）
    level         VARCHAR(8)   NOT NULL COMMENT '告警级别',
    -- 命中的自定义关键字（内置关键字或 NULL）
    keyword       VARCHAR(128) NULL COMMENT '命中关键字',
    -- 告警摘要（日志首行）
    message       TEXT         NOT NULL COMMENT '告警摘要',
    -- 完整日志内容（含堆栈）
    stack_trace   TEXT         NULL COMMENT '完整日志内容',
    -- 分析结论（L3/L4 产出，含降级标注）
    analysis      TEXT         NULL COMMENT '分析结论',
    -- 解决建议
    suggestion    TEXT         NULL COMMENT '解决建议',
    -- 到达的最高分析层级（0-4）
    layer_reached INT          NOT NULL COMMENT '最高分析层级',
    -- 时间窗内合并发生次数
    occurrence    INT          NOT NULL COMMENT '窗口内发生次数',
    -- 告警状态（OPEN/ACKED/RESOLVED）
    status        VARCHAR(16)  NOT NULL COMMENT '处理状态',
    -- 创建时间（UTC）
    created_at    DATETIME(6)  NOT NULL COMMENT '创建时间',
    -- 更新时间（UTC）
    updated_at    DATETIME(6)  NOT NULL COMMENT '更新时间',

    PRIMARY KEY (id),
    -- 同源告警定位
    INDEX idx_alert_fingerprint (fingerprint),
    -- 列表页按级别过滤 + 创建时间倒序
    INDEX idx_alert_level_created (level, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'logwatch 告警记录表';
