package com.smartops.agent.orchestrator;

import com.smartops.agent.worker.WorkerAgent;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.TaskStatus;
import com.smartops.common.exception.AgentException;
import com.smartops.common.model.A2aRequest;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentCard;
import com.smartops.common.model.SubTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TaskDispatcher} 单元测试。
 *
 * <p>验证任务分发器的核心契约：
 * <ul>
 *   <li>Worker 注册：正常注册、null 校验、同角色重复注册抛 AgentException</li>
 *   <li>任务分发：找到 Worker 时调用 handle，未找到时返回失败响应</li>
 *   <li>构造的 A2A 请求包含正确的源/目标角色与指令</li>
 *   <li>workerCount / hasWorker 查询</li>
 * </ul></p>
 *
 * <p>对应 agent.md 阶段三特性4（任务分发器）。</p>
 *
 * <p><b>测试策略</b>：
 * <ul>
 *   <li>Mock {@link WorkerAgent}，隔离所有依赖</li>
 *   <li>使用 AssertJ 断言，遵循 Arrange-Act-Assert 三段式</li>
 *   <li>使用 {@code @Nested} 分组，{@code @DisplayName} 中文描述</li>
 *   <li>测试方法命名 {@code should_{期望行为}_when_{前置条件}}</li>
 * </ul></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class TaskDispatcherTest {

    private TaskDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new TaskDispatcher();
    }

    /**
     * 创建带指定角色的 mock WorkerAgent。
     *
     * @param role Agent 角色
     * @return mock 的 WorkerAgent
     */
    private WorkerAgent mockWorker(AgentRole role) {
        WorkerAgent worker = mock(WorkerAgent.class);
        AgentCard card = new AgentCard(
                role.name().toLowerCase() + "-agent", role,
                role.getDisplayName() + "Agent", "desc",
                Set.of(), Set.of(), 1);
        when(worker.getCard()).thenReturn(card);
        return worker;
    }

    /**
     * 创建带指定角色的 SubTask。
     *
     * @param role 目标角色
     * @return 子任务
     */
    private SubTask subTask(AgentRole role) {
        return SubTask.create(
                "task-" + role.name(), "parent-001", role,
                "执行" + role.getDisplayName() + "任务", 1);
    }

    @Nested
    @DisplayName("registerWorker 注册 Worker")
    class RegisterWorker {

        @Test
        @DisplayName("正常注册 Worker 后可查询到")
        void should_registerWorker_when_validWorker() {
            WorkerAgent worker = mockWorker(AgentRole.MONITOR);

            dispatcher.registerWorker(worker);

            assertThat(dispatcher.workerCount()).isEqualTo(1);
            assertThat(dispatcher.hasWorker(AgentRole.MONITOR)).isTrue();
        }

        @Test
        @DisplayName("注册 null Worker 抛出 NullPointerException")
        void should_throwNpe_when_workerIsNull() {
            assertThatThrownBy(() -> dispatcher.registerWorker(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("worker");
        }

        @Test
        @DisplayName("同角色重复注册不同实例时抛出 AgentException")
        void should_throwAgentException_when_sameRoleDifferentInstance() {
            WorkerAgent worker1 = mockWorker(AgentRole.MONITOR);
            WorkerAgent worker2 = mockWorker(AgentRole.MONITOR);

            dispatcher.registerWorker(worker1);

            assertThatThrownBy(() -> dispatcher.registerWorker(worker2))
                    .isInstanceOf(AgentException.class)
                    .hasMessageContaining("MONITOR")
                    .hasMessageContaining("拒绝重复注册");
        }

        @Test
        @DisplayName("同实例重复注册幂等不抛异常")
        void should_beIdempotent_when_sameInstanceRegisteredTwice() {
            WorkerAgent worker = mockWorker(AgentRole.MONITOR);

            dispatcher.registerWorker(worker);
            dispatcher.registerWorker(worker);

            assertThat(dispatcher.workerCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("注册多个不同角色的 Worker 后数量正确")
        void should_incrementCount_when_multipleRolesRegistered() {
            dispatcher.registerWorker(mockWorker(AgentRole.MONITOR));
            dispatcher.registerWorker(mockWorker(AgentRole.ANALYZE));
            dispatcher.registerWorker(mockWorker(AgentRole.EXECUTE));

            assertThat(dispatcher.workerCount()).isEqualTo(3);
            assertThat(dispatcher.hasWorker(AgentRole.MONITOR)).isTrue();
            assertThat(dispatcher.hasWorker(AgentRole.ANALYZE)).isTrue();
            assertThat(dispatcher.hasWorker(AgentRole.EXECUTE)).isTrue();
        }
    }

    @Nested
    @DisplayName("dispatch 分发子任务")
    class Dispatch {

        @Test
        @DisplayName("找到已注册 Worker 时调用 handle 并返回响应")
        void should_callWorkerHandle_when_workerRegistered() {
            WorkerAgent worker = mockWorker(AgentRole.MONITOR);
            dispatcher.registerWorker(worker);

            A2aResponse expected = A2aResponse.success(
                    "req-001", "task-MONITOR", AgentRole.MONITOR, "监控结果");
            when(worker.handle(any(A2aRequest.class))).thenReturn(expected);

            SubTask task = subTask(AgentRole.MONITOR);
            A2aResponse response = dispatcher.dispatch(task);

            assertThat(response).isEqualTo(expected);
            verify(worker).handle(any(A2aRequest.class));
        }

        @Test
        @DisplayName("subTask 为 null 抛出 NullPointerException")
        void should_throwNpe_when_subTaskIsNull() {
            assertThatThrownBy(() -> dispatcher.dispatch(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("subTask");
        }

        @Test
        @DisplayName("Worker 未注册时返回失败响应")
        void should_returnFailure_when_workerNotRegistered() {
            SubTask task = subTask(AgentRole.MONITOR);
            A2aResponse response = dispatcher.dispatch(task);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(response.error()).contains("未找到角色为 MONITOR 的 Worker");
            assertThat(response.taskId()).isEqualTo(task.taskId());
            assertThat(response.sourceRole()).isEqualTo(AgentRole.MONITOR);
        }

        @Test
        @DisplayName("分发器构造的 A2A 请求包含正确的源/目标角色与指令")
        void should_constructCorrectRequest_when_dispatching() {
            WorkerAgent worker = mockWorker(AgentRole.MONITOR);
            dispatcher.registerWorker(worker);

            when(worker.handle(any(A2aRequest.class))).thenAnswer(invocation -> {
                A2aRequest req = invocation.getArgument(0);
                return A2aResponse.success(req.requestId(), req.taskId(),
                        req.targetRole(), "ok");
            });

            SubTask task = subTask(AgentRole.MONITOR);
            dispatcher.dispatch(task);

            verify(worker).handle(argThat(req ->
                    req.sourceRole() == AgentRole.SUPERVISOR
                            && req.targetRole() == AgentRole.MONITOR
                            && req.instruction().equals(task.instruction())
                            && req.taskId().equals(task.taskId())
                            && req.conversationId().equals(task.parentTaskId())
                            && req.requestId() != null
                            && !req.requestId().isBlank()
            ));
        }

        @Test
        @DisplayName("分发成功时返回 Worker 的响应")
        void should_returnWorkerResponse_when_dispatchSucceeds() {
            WorkerAgent worker = mockWorker(AgentRole.ANALYZE);
            dispatcher.registerWorker(worker);

            A2aResponse expected = A2aResponse.failure(
                    "req-002", "task-ANALYZE", AgentRole.ANALYZE, "分析超时");
            when(worker.handle(any(A2aRequest.class))).thenReturn(expected);

            A2aResponse response = dispatcher.dispatch(subTask(AgentRole.ANALYZE));

            assertThat(response).isEqualTo(expected);
            assertThat(response.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("workerCount 与 hasWorker")
    class CountAndHas {

        @Test
        @DisplayName("初始状态 workerCount 为 0")
        void should_returnZero_when_noWorkerRegistered() {
            assertThat(dispatcher.workerCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("hasWorker null 角色返回 false")
        void should_returnFalse_when_roleIsNull() {
            assertThat(dispatcher.hasWorker(null)).isFalse();
        }

        @Test
        @DisplayName("hasWorker 未注册角色返回 false")
        void should_returnFalse_when_roleNotRegistered() {
            assertThat(dispatcher.hasWorker(AgentRole.MONITOR)).isFalse();
        }

        @Test
        @DisplayName("hasWorker 已注册角色返回 true")
        void should_returnTrue_when_roleRegistered() {
            dispatcher.registerWorker(mockWorker(AgentRole.MONITOR));

            assertThat(dispatcher.hasWorker(AgentRole.MONITOR)).isTrue();
        }

        @Test
        @DisplayName("注册后 workerCount 正确递增")
        void should_incrementCount_when_workersRegistered() {
            assertThat(dispatcher.workerCount()).isEqualTo(0);

            dispatcher.registerWorker(mockWorker(AgentRole.MONITOR));
            assertThat(dispatcher.workerCount()).isEqualTo(1);

            dispatcher.registerWorker(mockWorker(AgentRole.ANALYZE));
            assertThat(dispatcher.workerCount()).isEqualTo(2);
        }
    }
}
