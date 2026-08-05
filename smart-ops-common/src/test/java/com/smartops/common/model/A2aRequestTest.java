package com.smartops.common.model;

import com.smartops.common.enums.AgentRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link A2aRequest} 单元测试。
 *
 * <p>验证 A2A 请求消息的构造、字段校验及角色约束。
 * 对应 agent.md 阶段三 Agent 间通信机制。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class A2aRequestTest {

    private static final String REQUEST_ID = "req-001";
    private static final String TASK_ID = "task-001";
    private static final String INSTRUCTION = "查询 CPU 使用率";
    private static final String CONVERSATION_ID = "conv-001";

    @Nested
    @DisplayName("构造与校验")
    class Construction {

        @Test
        @DisplayName("合法参数构造成功且字段全部正确")
        void should_construct_when_validParams() {
            A2aRequest request = new A2aRequest(
                    REQUEST_ID, TASK_ID, AgentRole.SUPERVISOR, AgentRole.MONITOR,
                    INSTRUCTION, CONVERSATION_ID
            );

            assertThat(request.requestId()).isEqualTo(REQUEST_ID);
            assertThat(request.taskId()).isEqualTo(TASK_ID);
            assertThat(request.sourceRole()).isEqualTo(AgentRole.SUPERVISOR);
            assertThat(request.targetRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(request.instruction()).isEqualTo(INSTRUCTION);
            assertThat(request.conversationId()).isEqualTo(CONVERSATION_ID);
        }

        @Test
        @DisplayName("conversationId 为 null 时构造成功")
        void should_construct_when_conversationIdIsNull() {
            A2aRequest request = new A2aRequest(
                    REQUEST_ID, TASK_ID, AgentRole.SUPERVISOR, AgentRole.MONITOR,
                    INSTRUCTION, null
            );

            assertThat(request.conversationId()).isNull();
        }

        @Test
        @DisplayName("两个不同 Worker 角色互发时构造成功")
        void should_construct_when_bothRolesAreWorkers() {
            A2aRequest request = new A2aRequest(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR, AgentRole.ANALYZE,
                    INSTRUCTION, CONVERSATION_ID
            );

            assertThat(request.sourceRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(request.targetRole()).isEqualTo(AgentRole.ANALYZE);
        }

        @Test
        @DisplayName("requestId 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_requestIdIsNull() {
            assertThatThrownBy(() -> new A2aRequest(
                    null, TASK_ID, AgentRole.SUPERVISOR, AgentRole.MONITOR,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("requestId");
        }

        @Test
        @DisplayName("taskId 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_taskIdIsNull() {
            assertThatThrownBy(() -> new A2aRequest(
                    REQUEST_ID, null, AgentRole.SUPERVISOR, AgentRole.MONITOR,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("taskId");
        }

        @Test
        @DisplayName("sourceRole 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_sourceRoleIsNull() {
            assertThatThrownBy(() -> new A2aRequest(
                    REQUEST_ID, TASK_ID, null, AgentRole.MONITOR,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("sourceRole");
        }

        @Test
        @DisplayName("targetRole 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_targetRoleIsNull() {
            assertThatThrownBy(() -> new A2aRequest(
                    REQUEST_ID, TASK_ID, AgentRole.SUPERVISOR, null,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("targetRole");
        }

        @Test
        @DisplayName("requestId 为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_requestIdIsBlank() {
            assertThatThrownBy(() -> new A2aRequest(
                    "   ", TASK_ID, AgentRole.SUPERVISOR, AgentRole.MONITOR,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requestId");
        }

        @Test
        @DisplayName("requestId 为空串时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_requestIdIsEmpty() {
            assertThatThrownBy(() -> new A2aRequest(
                    "", TASK_ID, AgentRole.SUPERVISOR, AgentRole.MONITOR,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requestId");
        }

        @Test
        @DisplayName("taskId 为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_taskIdIsBlank() {
            assertThatThrownBy(() -> new A2aRequest(
                    REQUEST_ID, "   ", AgentRole.SUPERVISOR, AgentRole.MONITOR,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taskId");
        }

        @Test
        @DisplayName("taskId 为空串时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_taskIdIsEmpty() {
            assertThatThrownBy(() -> new A2aRequest(
                    REQUEST_ID, "", AgentRole.SUPERVISOR, AgentRole.MONITOR,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taskId");
        }

        @Test
        @DisplayName("instruction 为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_instructionIsNull() {
            assertThatThrownBy(() -> new A2aRequest(
                    REQUEST_ID, TASK_ID, AgentRole.SUPERVISOR, AgentRole.MONITOR,
                    null, CONVERSATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("instruction");
        }

        @Test
        @DisplayName("instruction 为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_instructionIsBlank() {
            assertThatThrownBy(() -> new A2aRequest(
                    REQUEST_ID, TASK_ID, AgentRole.SUPERVISOR, AgentRole.MONITOR,
                    "   ", CONVERSATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("instruction");
        }
    }

    @Nested
    @DisplayName("角色约束")
    class RoleConstraints {

        @Test
        @DisplayName("targetRole 为 SUPERVISOR 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_targetRoleIsSupervisor() {
            assertThatThrownBy(() -> new A2aRequest(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR, AgentRole.SUPERVISOR,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SUPERVISOR");
        }

        @Test
        @DisplayName("sourceRole 与 targetRole 相同时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_sourceRoleEqualsTargetRole() {
            assertThatThrownBy(() -> new A2aRequest(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR, AgentRole.MONITOR,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("相同");
        }

        @Test
        @DisplayName("sourceRole 与 targetRole 均为 SUPERVISOR 时抛出异常")
        void should_throwIllegalArg_when_bothRolesAreSupervisor() {
            assertThatThrownBy(() -> new A2aRequest(
                    REQUEST_ID, TASK_ID, AgentRole.SUPERVISOR, AgentRole.SUPERVISOR,
                    INSTRUCTION, CONVERSATION_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
