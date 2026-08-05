-- ============================================================================
-- V2__audit_event.sql — 阶段五 L2 操作审计事件表
--
-- 背景：生产环境 spring.jpa.hibernate.ddl-auto=validate，新表必须随发布手工执行。
-- 对应实体：smart-ops-infrastructure persistence/audit/AuditEventEntity.java
-- 执行方式：mysql -u<user> -p <database> < docs/sql/V2__audit_event.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS audit_event (
    -- 主键（自增）
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    -- 事件类型（LLM_CALL/TOOL_CALL/TASK_EXECUTION/SECURITY_DECISION）
    event_type  VARCHAR(24)  NOT NULL COMMENT '事件类型',
    -- 关联标识（conversationId / logwatch 指纹前缀，可空）
    trace_id    VARCHAR(128) NULL COMMENT '关联标识',
    -- 操作发起者（chatService/agentRouter/工具名等）
    actor       VARCHAR(128) NOT NULL COMMENT '操作发起者',
    -- 操作目标（模型名/工具名/执行模式，可空）
    target      VARCHAR(255) NULL COMMENT '操作目标',
    -- 操作摘要（入参/结果截断，可空）
    detail      TEXT         NULL COMMENT '操作摘要',
    -- 操作是否成功
    success     BIT(1)       NOT NULL COMMENT '是否成功',
    -- 操作耗时（毫秒）
    latency_ms  BIGINT       NOT NULL COMMENT '耗时毫秒',
    -- 发生时间（UTC）
    created_at  DATETIME(6)  NOT NULL COMMENT '发生时间',

    PRIMARY KEY (id),
    -- 列表页按事件类型过滤 + 发生时间倒序
    INDEX idx_audit_type_created (event_type, created_at),
    -- 按会话/指纹追踪一条链路的全部操作
    INDEX idx_audit_trace (trace_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'L2 操作审计事件表';
