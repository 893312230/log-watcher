package com.smartops.common.model;

import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SubTask} 单元测试。
 *
 * <p>验证子任务的构造、字段校验、工厂方法、状态变更与终态判定。
 * 对应 agent.md 阶段三 Supervisor-Worker 任务分解机制。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class SubTaskTest {

    private static final String TASK_ID = "task-001";
    private static final String PARENT_TASK_ID = "parent-001";
    private static final String INSTRUCTION = "查询 CPU 使用率";

    @Nested
    @DisplayName("构造与校验")
    class Construction {

        @Test
        @DisplayName("合法参数构造成功且字段全部正确")
        void should_construct_when_validParams() {
            SubTask task = new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, TaskStatus.CREATED, null
            );

            assertThat(task.taskId()).isEqualTo(TASK_ID);
            assertThat(task.parentTaskId()).isEqualTo(PARENT_TASK_ID);
            assertThat(task.targetRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(task.instruction()).isEqualTo(INSTRUCTION);
            assertThat(task.priority()).isEqualTo(5);
            assertThat(task.status()).isEqualTo(TaskStatus.CREATED);
            assertThat(task.result()).isNull();
        }

        @Test
        @DisplayName("parentTaskId 为 null 时构造成功")
        void should_construct_when_parentTaskIdIsNull() {
            SubTask task = new SubTask(
                    TASK_ID, null, AgentRole.MONITOR,
                    INSTRUCTION, 5, TaskStatus.CREATED, null
            );

            assertThat(task.parentTaskId()).isNull();
        }

        @Test
        @DisplayName("result 非 null 时构造成功")
        void should_construct_when_resultProvided() {
            SubTask task = new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, TaskStatus.SUCCESS, "CPU 30%"
            );

            assertThat(task.result()).isEqualTo("CPU 30%");
        }

        @Test
        @DisplayName("priority 为边界值 1 时构造成功")
        void should_construct_when_priorityAtLowerBoundary() {
            SubTask task = new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 1, TaskStatus.CREATED, null
            );

            assertThat(task.priority()).isEqualTo(1);
        }

        @Test
        @DisplayName("priority 为边界值 10 时构造成功")
        void should_construct_when_priorityAtUpperBoundary() {
            SubTask task = new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 10, TaskStatus.CREATED, null
            );

            assertThat(task.priority()).isEqualTo(10);
        }

        @Test
        @DisplayName("taskId 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_taskIdIsNull() {
            assertThatThrownBy(() -> new SubTask(
                    null, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, TaskStatus.CREATED, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("taskId");
        }

        @Test
        @DisplayName("targetRole 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_targetRoleIsNull() {
            assertThatThrownBy(() -> new SubTask(
                    TASK_ID, PARENT_TASK_ID, null,
                    INSTRUCTION, 5, TaskStatus.CREATED, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("targetRole");
        }

        @Test
        @DisplayName("status 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_statusIsNull() {
            assertThatThrownBy(() -> new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("status");
        }

        @Test
        @DisplayName("taskId 为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_taskIdIsBlank() {
            assertThatThrownBy(() -> new SubTask(
                    "   ", PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, TaskStatus.CREATED, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taskId");
        }

        @Test
        @DisplayName("taskId 为空串时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_taskIdIsEmpty() {
            assertThatThrownBy(() -> new SubTask(
                    "", PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, TaskStatus.CREATED, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taskId");
        }

        @Test
        @DisplayName("instruction 为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_instructionIsNull() {
            assertThatThrownBy(() -> new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    null, 5, TaskStatus.CREATED, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("instruction");
        }

        @Test
        @DisplayName("instruction 为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_instructionIsBlank() {
            assertThatThrownBy(() -> new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    "   ", 5, TaskStatus.CREATED, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("instruction");
        }

        @Test
        @DisplayName("priority 小于 1 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_priorityBelowOne() {
            assertThatThrownBy(() -> new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 0, TaskStatus.CREATED, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("priority");
        }

        @Test
        @DisplayName("priority 大于 10 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_priorityAboveTen() {
            assertThatThrownBy(() -> new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 11, TaskStatus.CREATED, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("priority");
        }

        @Test
        @DisplayName("priority 为负数时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_priorityNegative() {
            assertThatThrownBy(() -> new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, -1, TaskStatus.CREATED, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("priority");
        }

        @Test
        @DisplayName("targetRole 为 SUPERVISOR 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_targetRoleIsSupervisor() {
            assertThatThrownBy(() -> new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.SUPERVISOR,
                    INSTRUCTION, 5, TaskStatus.CREATED, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SUPERVISOR");
        }
    }

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("create 创建的子任务状态为 CREATED、结果为 null")
        void should_createWithCreatedStatus_when_createCalled() {
            SubTask task = SubTask.create(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR, INSTRUCTION, 5
            );

            assertThat(task.taskId()).isEqualTo(TASK_ID);
            assertThat(task.parentTaskId()).isEqualTo(PARENT_TASK_ID);
            assertThat(task.targetRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(task.instruction()).isEqualTo(INSTRUCTION);
            assertThat(task.priority()).isEqualTo(5);
            assertThat(task.status()).isEqualTo(TaskStatus.CREATED);
            assertThat(task.result()).isNull();
        }

        @Test
        @DisplayName("create 创建的子任务 parentTaskId 可为 null")
        void should_create_when_parentTaskIdIsNull() {
            SubTask task = SubTask.create(
                    TASK_ID, null, AgentRole.ANALYZE, INSTRUCTION, 3
            );

            assertThat(task.parentTaskId()).isNull();
            assertThat(task.status()).isEqualTo(TaskStatus.CREATED);
        }

        @Test
        @DisplayName("create 传入非法角色时抛出异常")
        void should_throw_when_createWithSupervisorRole() {
            assertThatThrownBy(() -> SubTask.create(
                    TASK_ID, PARENT_TASK_ID, AgentRole.SUPERVISOR, INSTRUCTION, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SUPERVISOR");
        }
    }

    @Nested
    @DisplayName("状态变更方法")
    class StateTransition {

        @Test
        @DisplayName("withStatus 返回新实例且状态更新")
        void should_returnNewInstance_when_withStatusCalled() {
            SubTask original = SubTask.create(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR, INSTRUCTION, 5
            );

            SubTask updated = original.withStatus(TaskStatus.RUNNING);

            assertThat(updated).isNotSameAs(original);
            assertThat(updated.status()).isEqualTo(TaskStatus.RUNNING);
            assertThat(updated.taskId()).isEqualTo(TASK_ID);
            assertThat(updated.parentTaskId()).isEqualTo(PARENT_TASK_ID);
            assertThat(updated.targetRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(updated.instruction()).isEqualTo(INSTRUCTION);
            assertThat(updated.priority()).isEqualTo(5);
            assertThat(updated.result()).isNull();
        }

        @Test
        @DisplayName("withStatus 不修改原实例")
        void should_keepOriginalStatus_when_withStatusCalled() {
            SubTask original = SubTask.create(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR, INSTRUCTION, 5
            );

            original.withStatus(TaskStatus.RUNNING);

            assertThat(original.status()).isEqualTo(TaskStatus.CREATED);
        }

        @Test
        @DisplayName("withResult 返回新实例且状态与结果均更新")
        void should_returnNewInstance_when_withResultCalled() {
            SubTask original = SubTask.create(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR, INSTRUCTION, 5
            );

            SubTask updated = original.withResult(TaskStatus.SUCCESS, "CPU 30%");

            assertThat(updated).isNotSameAs(original);
            assertThat(updated.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(updated.result()).isEqualTo("CPU 30%");
            assertThat(updated.taskId()).isEqualTo(TASK_ID);
            assertThat(updated.parentTaskId()).isEqualTo(PARENT_TASK_ID);
            assertThat(updated.targetRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(updated.instruction()).isEqualTo(INSTRUCTION);
            assertThat(updated.priority()).isEqualTo(5);
        }

        @Test
        @DisplayName("withResult 传入 null 结果时构造成功")
        void should_construct_when_withResultNull() {
            SubTask original = SubTask.create(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR, INSTRUCTION, 5
            );

            SubTask updated = original.withResult(TaskStatus.FAILED, null);

            assertThat(updated.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(updated.result()).isNull();
        }

        @Test
        @DisplayName("withResult 不修改原实例")
        void should_keepOriginalResult_when_withResultCalled() {
            SubTask original = SubTask.create(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR, INSTRUCTION, 5
            );

            original.withResult(TaskStatus.SUCCESS, "CPU 30%");

            assertThat(original.status()).isEqualTo(TaskStatus.CREATED);
            assertThat(original.result()).isNull();
        }
    }

    @Nested
    @DisplayName("isTerminal 方法")
    class IsTerminal {

        @Test
        @DisplayName("状态为 SUCCESS 时返回 true")
        void should_returnTrue_when_statusIsSuccess() {
            SubTask task = new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, TaskStatus.SUCCESS, "ok"
            );

            assertThat(task.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("状态为 FAILED 时返回 true")
        void should_returnTrue_when_statusIsFailed() {
            SubTask task = new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, TaskStatus.FAILED, null
            );

            assertThat(task.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("状态为 CANCELLED 时返回 true")
        void should_returnTrue_when_statusIsCancelled() {
            SubTask task = new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, TaskStatus.CANCELLED, null
            );

            assertThat(task.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("状态为 CREATED 时返回 false")
        void should_returnFalse_when_statusIsCreated() {
            SubTask task = SubTask.create(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR, INSTRUCTION, 5
            );

            assertThat(task.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("状态为 RUNNING 时返回 false")
        void should_returnFalse_when_statusIsRunning() {
            SubTask task = new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, TaskStatus.RUNNING, null
            );

            assertThat(task.isTerminal()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(mode = EnumSource.Mode.INCLUDE, names = {"SUCCESS", "FAILED", "CANCELLED"})
        @DisplayName("终态状态下均返回 true")
        void should_returnTrue_when_statusIsTerminal(TaskStatus status) {
            SubTask task = new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, status, null
            );

            assertThat(task.isTerminal()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(mode = EnumSource.Mode.INCLUDE, names = {"CREATED", "RUNNING"})
        @DisplayName("非终态状态下均返回 false")
        void should_returnFalse_when_statusIsNotTerminal(TaskStatus status) {
            SubTask task = new SubTask(
                    TASK_ID, PARENT_TASK_ID, AgentRole.MONITOR,
                    INSTRUCTION, 5, status, null
            );

            assertThat(task.isTerminal()).isFalse();
        }
    }
}
