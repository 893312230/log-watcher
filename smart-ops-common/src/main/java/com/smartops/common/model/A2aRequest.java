package com.smartops.common.model;

import com.smartops.common.enums.AgentRole;

import java.util.Objects;

/**
 * A2A 请求。
 *
 * <p>Agent 间通信的请求消息，由 Supervisor 发送给 Worker。
 * 封装子任务 ID、目标角色、执行指令和会话上下文。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param requestId      请求唯一标识
 * @param taskId         子任务 ID
 * @param sourceRole     发送方角色
 * @param targetRole     接收方角色
 * @param instruction    执行指令
 * @param conversationId 会话 ID（用于关联短期记忆）
 */
public record A2aRequest(
        String requestId,
        String taskId,
        AgentRole sourceRole,
        AgentRole targetRole,
        String instruction,
        String conversationId
) {

    /**
     * 紧凑构造器：校验必填字段。
     */
    public A2aRequest {
        Objects.requireNonNull(requestId, "requestId 不能为 null");
        Objects.requireNonNull(taskId, "taskId 不能为 null");
        Objects.requireNonNull(sourceRole, "sourceRole 不能为 null");
        Objects.requireNonNull(targetRole, "targetRole 不能为 null");
        if (requestId.isBlank()) {
            throw new IllegalArgumentException("requestId 不能为空白");
        }
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空白");
        }
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("instruction 不能为 null 或空白");
        }
        if (targetRole == AgentRole.SUPERVISOR) {
            throw new IllegalArgumentException("A2A 请求目标角色不能是 SUPERVISOR");
        }
        if (sourceRole == targetRole) {
            throw new IllegalArgumentException("发送方与接收方角色不能相同");
        }
    }
}
