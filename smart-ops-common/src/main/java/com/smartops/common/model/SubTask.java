package com.smartops.common.model;

import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.TaskStatus;

import java.util.Objects;

/**
 * 子任务。
 *
 * <p>Supervisor 将复杂任务分解为多个子任务，每个子任务分配给一个 Worker Agent 执行。
 * 子任务包含执行指令、目标角色、优先级和状态信息。</p>
 *
 * <p>线程安全：record 不可变，状态变更通过 {@link #withStatus} 返回新实例。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param taskId      子任务唯一标识
 * @param parentTaskId 父任务标识
 * @param targetRole  目标执行角色
 * @param instruction 执行指令（自然语言描述）
 * @param priority    优先级（1-10，1 最高）
 * @param status      任务状态
 * @param result      执行结果（完成前为 null）
 */
public record SubTask(
        String taskId,
        String parentTaskId,
        AgentRole targetRole,
        String instruction,
        int priority,
        TaskStatus status,
        String result
) {

    /**
     * 紧凑构造器：校验必填字段。
     */
    public SubTask {
        Objects.requireNonNull(taskId, "taskId 不能为 null");
        Objects.requireNonNull(targetRole, "targetRole 不能为 null");
        Objects.requireNonNull(status, "status 不能为 null");
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空白");
        }
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("instruction 不能为 null 或空白");
        }
        if (priority < 1 || priority > 10) {
            throw new IllegalArgumentException("priority 必须在 1-10 之间");
        }
        if (targetRole == AgentRole.SUPERVISOR) {
            throw new IllegalArgumentException("子任务目标角色不能是 SUPERVISOR");
        }
    }

    /**
     * 创建一个新子任务（状态为 CREATED，结果为 null）。
     *
     * @param taskId       子任务 ID
     * @param parentTaskId 父任务 ID
     * @param targetRole   目标角色
     * @param instruction  执行指令
     * @param priority     优先级
     * @return 新建的子任务
     */
    public static SubTask create(String taskId, String parentTaskId,
                                 AgentRole targetRole, String instruction, int priority) {
        return new SubTask(taskId, parentTaskId, targetRole, instruction,
                priority, TaskStatus.CREATED, null);
    }

    /**
     * 更新任务状态，返回新实例。
     *
     * @param newStatus 新状态
     * @return 更新后的子任务
     */
    public SubTask withStatus(TaskStatus newStatus) {
        return new SubTask(taskId, parentTaskId, targetRole, instruction,
                priority, newStatus, result);
    }

    /**
     * 更新任务状态和结果，返回新实例。
     *
     * @param newStatus 新状态
     * @param newResult 执行结果
     * @return 更新后的子任务
     */
    public SubTask withResult(TaskStatus newStatus, String newResult) {
        return new SubTask(taskId, parentTaskId, targetRole, instruction,
                priority, newStatus, newResult);
    }

    /**
     * 判断任务是否已终结（成功/失败/取消）。
     *
     * @return 如果任务已终结返回 true
     */
    public boolean isTerminal() {
        return status == TaskStatus.SUCCESS
                || status == TaskStatus.FAILED
                || status == TaskStatus.CANCELLED;
    }
}
