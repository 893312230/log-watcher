package com.smartops.common.model;

import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.TaskStatus;

import java.util.Objects;

/**
 * A2A 响应。
 *
 * <p>Worker 执行完子任务后返回给 Supervisor 的响应消息。
 * 封装执行结果、状态和错误信息。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param requestId  对应的请求 ID
 * @param taskId     子任务 ID
 * @param sourceRole 执行方角色（Worker）
 * @param status     执行状态
 * @param result     执行结果（成功时非 null）
 * @param error      错误信息（失败时非 null）
 */
public record A2aResponse(
        String requestId,
        String taskId,
        AgentRole sourceRole,
        TaskStatus status,
        String result,
        String error
) {

    /**
     * 紧凑构造器：校验必填字段。
     */
    public A2aResponse {
        Objects.requireNonNull(requestId, "requestId 不能为 null");
        Objects.requireNonNull(taskId, "taskId 不能为 null");
        Objects.requireNonNull(sourceRole, "sourceRole 不能为 null");
        Objects.requireNonNull(status, "status 不能为 null");
        if (requestId.isBlank()) {
            throw new IllegalArgumentException("requestId 不能为空白");
        }
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空白");
        }
        if (sourceRole == AgentRole.SUPERVISOR) {
            throw new IllegalArgumentException("A2A 响应来源角色不能是 SUPERVISOR");
        }
    }

    /**
     * 构造成功响应。
     *
     * @param requestId 请求 ID
     * @param taskId    子任务 ID
     * @param sourceRole 执行方角色
     * @param result    执行结果
     * @return 成功的 A2A 响应
     */
    public static A2aResponse success(String requestId, String taskId,
                                      AgentRole sourceRole, String result) {
        return new A2aResponse(requestId, taskId, sourceRole,
                TaskStatus.SUCCESS, result, null);
    }

    /**
     * 构造失败响应。
     *
     * @param requestId  请求 ID
     * @param taskId     子任务 ID
     * @param sourceRole 执行方角色
     * @param error      错误信息
     * @return 失败的 A2A 响应
     */
    public static A2aResponse failure(String requestId, String taskId,
                                      AgentRole sourceRole, String error) {
        return new A2aResponse(requestId, taskId, sourceRole,
                TaskStatus.FAILED, null, error);
    }

    /**
     * 判断响应是否成功。
     *
     * @return 如果执行成功返回 true
     */
    public boolean isSuccess() {
        return status == TaskStatus.SUCCESS;
    }
}
