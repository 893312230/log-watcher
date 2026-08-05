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
 * {@link A2aResponse} 单元测试。
 *
 * <p>验证 A2A 响应消息的构造、字段校验、工厂方法及成功判定。
 * 对应 agent.md 阶段三 Agent 间通信机制。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class A2aResponseTest {

    private static final String REQUEST_ID = "req-001";
    private static final String TASK_ID = "task-001";
    private static final String RESULT = "CPU 使用率 30%";
    private static final String ERROR = "执行超时";

    @Nested
    @DisplayName("构造与校验")
    class Construction {

        @Test
        @DisplayName("合法参数构造成功且字段全部正确")
        void should_construct_when_validParams() {
            A2aResponse response = new A2aResponse(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR,
                    TaskStatus.SUCCESS, RESULT, null
            );

            assertThat(response.requestId()).isEqualTo(REQUEST_ID);
            assertThat(response.taskId()).isEqualTo(TASK_ID);
            assertThat(response.sourceRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(response.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(response.result()).isEqualTo(RESULT);
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("result 为 null 时构造成功")
        void should_construct_when_resultIsNull() {
            A2aResponse response = new A2aResponse(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR,
                    TaskStatus.FAILED, null, ERROR
            );

            assertThat(response.result()).isNull();
            assertThat(response.error()).isEqualTo(ERROR);
        }

        @Test
        @DisplayName("error 为 null 时构造成功")
        void should_construct_when_errorIsNull() {
            A2aResponse response = new A2aResponse(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR,
                    TaskStatus.SUCCESS, RESULT, null
            );

            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("result 与 error 均为 null 时构造成功")
        void should_construct_when_bothResultAndErrorNull() {
            A2aResponse response = new A2aResponse(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR,
                    TaskStatus.RUNNING, null, null
            );

            assertThat(response.result()).isNull();
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("requestId 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_requestIdIsNull() {
            assertThatThrownBy(() -> new A2aResponse(
                    null, TASK_ID, AgentRole.MONITOR,
                    TaskStatus.SUCCESS, RESULT, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("requestId");
        }

        @Test
        @DisplayName("taskId 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_taskIdIsNull() {
            assertThatThrownBy(() -> new A2aResponse(
                    REQUEST_ID, null, AgentRole.MONITOR,
                    TaskStatus.SUCCESS, RESULT, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("taskId");
        }

        @Test
        @DisplayName("sourceRole 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_sourceRoleIsNull() {
            assertThatThrownBy(() -> new A2aResponse(
                    REQUEST_ID, TASK_ID, null,
                    TaskStatus.SUCCESS, RESULT, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("sourceRole");
        }

        @Test
        @DisplayName("status 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_statusIsNull() {
            assertThatThrownBy(() -> new A2aResponse(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR,
                    null, RESULT, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("status");
        }

        @Test
        @DisplayName("requestId 为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_requestIdIsBlank() {
            assertThatThrownBy(() -> new A2aResponse(
                    "   ", TASK_ID, AgentRole.MONITOR,
                    TaskStatus.SUCCESS, RESULT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requestId");
        }

        @Test
        @DisplayName("requestId 为空串时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_requestIdIsEmpty() {
            assertThatThrownBy(() -> new A2aResponse(
                    "", TASK_ID, AgentRole.MONITOR,
                    TaskStatus.SUCCESS, RESULT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requestId");
        }

        @Test
        @DisplayName("taskId 为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_taskIdIsBlank() {
            assertThatThrownBy(() -> new A2aResponse(
                    REQUEST_ID, "   ", AgentRole.MONITOR,
                    TaskStatus.SUCCESS, RESULT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taskId");
        }

        @Test
        @DisplayName("taskId 为空串时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_taskIdIsEmpty() {
            assertThatThrownBy(() -> new A2aResponse(
                    REQUEST_ID, "", AgentRole.MONITOR,
                    TaskStatus.SUCCESS, RESULT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taskId");
        }

        @Test
        @DisplayName("sourceRole 为 SUPERVISOR 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_sourceRoleIsSupervisor() {
            assertThatThrownBy(() -> new A2aResponse(
                    REQUEST_ID, TASK_ID, AgentRole.SUPERVISOR,
                    TaskStatus.SUCCESS, RESULT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SUPERVISOR");
        }
    }

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("success 创建成功响应，状态为 SUCCESS 且 error 为 null")
        void should_createSuccessResponse_when_successCalled() {
            A2aResponse response = A2aResponse.success(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR, RESULT
            );

            assertThat(response.requestId()).isEqualTo(REQUEST_ID);
            assertThat(response.taskId()).isEqualTo(TASK_ID);
            assertThat(response.sourceRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(response.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(response.result()).isEqualTo(RESULT);
            assertThat(response.error()).isNull();
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("failure 创建失败响应，状态为 FAILED 且 result 为 null")
        void should_createFailureResponse_when_failureCalled() {
            A2aResponse response = A2aResponse.failure(
                    REQUEST_ID, TASK_ID, AgentRole.EXECUTE, ERROR
            );

            assertThat(response.requestId()).isEqualTo(REQUEST_ID);
            assertThat(response.taskId()).isEqualTo(TASK_ID);
            assertThat(response.sourceRole()).isEqualTo(AgentRole.EXECUTE);
            assertThat(response.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(response.result()).isNull();
            assertThat(response.error()).isEqualTo(ERROR);
            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("success 工厂传入 null result 时构造成功")
        void should_construct_when_successCalledWithNullResult() {
            A2aResponse response = A2aResponse.success(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR, null
            );

            assertThat(response.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(response.result()).isNull();
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("failure 工厂传入 null error 时构造成功")
        void should_construct_when_failureCalledWithNullError() {
            A2aResponse response = A2aResponse.failure(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR, null
            );

            assertThat(response.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(response.result()).isNull();
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("success 工厂传入 SUPERVISOR 角色时抛出异常")
        void should_throw_when_successCalledWithSupervisor() {
            assertThatThrownBy(() -> A2aResponse.success(
                    REQUEST_ID, TASK_ID, AgentRole.SUPERVISOR, RESULT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SUPERVISOR");
        }

        @Test
        @DisplayName("failure 工厂传入 SUPERVISOR 角色时抛出异常")
        void should_throw_when_failureCalledWithSupervisor() {
            assertThatThrownBy(() -> A2aResponse.failure(
                    REQUEST_ID, TASK_ID, AgentRole.SUPERVISOR, ERROR))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SUPERVISOR");
        }

        @Test
        @DisplayName("success 工厂传入空白 requestId 时抛出异常")
        void should_throw_when_successCalledWithBlankRequestId() {
            assertThatThrownBy(() -> A2aResponse.success(
                    "   ", TASK_ID, AgentRole.MONITOR, RESULT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requestId");
        }

        @Test
        @DisplayName("failure 工厂传入空白 taskId 时抛出异常")
        void should_throw_when_failureCalledWithBlankTaskId() {
            assertThatThrownBy(() -> A2aResponse.failure(
                    REQUEST_ID, "", AgentRole.MONITOR, ERROR))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taskId");
        }
    }

    @Nested
    @DisplayName("isSuccess 方法")
    class IsSuccess {

        @Test
        @DisplayName("状态为 SUCCESS 时返回 true")
        void should_returnTrue_when_statusIsSuccess() {
            A2aResponse response = A2aResponse.success(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR, RESULT
            );

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("状态为 FAILED 时返回 false")
        void should_returnFalse_when_statusIsFailed() {
            A2aResponse response = A2aResponse.failure(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR, ERROR
            );

            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("状态为 CREATED 时返回 false")
        void should_returnFalse_when_statusIsCreated() {
            A2aResponse response = new A2aResponse(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR,
                    TaskStatus.CREATED, null, null
            );

            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("状态为 RUNNING 时返回 false")
        void should_returnFalse_when_statusIsRunning() {
            A2aResponse response = new A2aResponse(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR,
                    TaskStatus.RUNNING, null, null
            );

            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("状态为 CANCELLED 时返回 false")
        void should_returnFalse_when_statusIsCancelled() {
            A2aResponse response = new A2aResponse(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR,
                    TaskStatus.CANCELLED, null, null
            );

            assertThat(response.isSuccess()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "SUCCESS")
        @DisplayName("非 SUCCESS 状态下均返回 false")
        void should_returnFalse_when_statusIsNotSuccess(TaskStatus status) {
            A2aResponse response = new A2aResponse(
                    REQUEST_ID, TASK_ID, AgentRole.MONITOR,
                    status, null, null
            );

            assertThat(response.isSuccess()).isFalse();
        }
    }
}
