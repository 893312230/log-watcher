package com.smartops.common.enums;

/**
 * 审计事件类型枚举（阶段五 L2 操作审计）。
 *
 * <p>描述被审计操作的类别，用于审计事件的分类查询。</p>
 *
 * <p>线程安全：枚举天然不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public enum AuditEventType {

    /**
     * LLM 调用：经 ChatService 汇聚点发出的每次大模型请求。
     */
    LLM_CALL,

    /**
     * 工具调用：Spring AI 工具执行（@Tool 方法 + MCP 工具）。
     */
    TOOL_CALL,

    /**
     * 任务执行：一次完整的用户任务（AgentRouter 统一边界）。
     */
    TASK_EXECUTION,

    /**
     * 安全决策：SecurityGate 高风险操作放行/拦截。
     */
    SECURITY_DECISION,
}
