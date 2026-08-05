package com.smartops.agent.orchestrator;

import com.smartops.agent.worker.WorkerAgent;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.model.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkerRegistrar} 单元测试。
 *
 * <p>验证 Worker 自动注册器的核心契约：
 * <ul>
 *   <li>应用启动后自动将所有 WorkerAgent Bean 注册到 {@link TaskDispatcher}</li>
 *   <li>Worker 列表为空或 null 时跳过注册并打印告警日志</li>
 *   <li>每个 Worker 都调用 dispatcher.registerWorker</li>
 * </ul></p>
 *
 * <p>对应 agent.md 阶段三 Worker 自动注册机制。</p>
 *
 * <p><b>测试策略</b>：
 * <ul>
 *   <li>Mock {@link TaskDispatcher} 与 {@link WorkerAgent}，隔离所有依赖</li>
 *   <li>使用 AssertJ 断言，遵循 Arrange-Act-Assert 三段式</li>
 *   <li>使用 {@code @Nested} 分组，{@code @DisplayName} 中文描述</li>
 *   <li>测试方法命名 {@code should_{期望行为}_when_{前置条件}}</li>
 * </ul></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class WorkerRegistrarTest {

    private TaskDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = mock(TaskDispatcher.class);
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

    @Nested
    @DisplayName("registerAllWorkers 注册所有 Worker")
    class RegisterAllWorkers {

        @Test
        @DisplayName("正常注册多个 Worker 到分发器")
        void should_registerAllWorkers_when_workersNotEmpty() {
            WorkerAgent monitor = mockWorker(AgentRole.MONITOR);
            WorkerAgent analyze = mockWorker(AgentRole.ANALYZE);
            WorkerAgent execute = mockWorker(AgentRole.EXECUTE);
            WorkerAgent knowledge = mockWorker(AgentRole.KNOWLEDGE);
            when(dispatcher.workerCount()).thenReturn(4);

            WorkerRegistrar registrar = new WorkerRegistrar(dispatcher,
                    List.of(monitor, analyze, execute, knowledge));

            registrar.registerAllWorkers();

            verify(dispatcher).registerWorker(monitor);
            verify(dispatcher).registerWorker(analyze);
            verify(dispatcher).registerWorker(execute);
            verify(dispatcher).registerWorker(knowledge);
            verify(dispatcher).workerCount();
        }

        @Test
        @DisplayName("Worker 列表为空时不注册任何 Worker")
        void should_notRegister_when_workersIsEmpty() {
            WorkerRegistrar registrar = new WorkerRegistrar(dispatcher, List.of());

            registrar.registerAllWorkers();

            verify(dispatcher, never()).registerWorker(any(WorkerAgent.class));
        }

        @Test
        @DisplayName("Worker 列表为 null 时不注册任何 Worker")
        void should_notRegister_when_workersIsNull() {
            WorkerRegistrar registrar = new WorkerRegistrar(dispatcher, null);

            registrar.registerAllWorkers();

            verify(dispatcher, never()).registerWorker(any(WorkerAgent.class));
        }

        @Test
        @DisplayName("单个 Worker 正常注册")
        void should_registerSingleWorker_when_onlyOneWorker() {
            WorkerAgent worker = mockWorker(AgentRole.MONITOR);
            when(dispatcher.workerCount()).thenReturn(1);

            WorkerRegistrar registrar = new WorkerRegistrar(dispatcher, List.of(worker));

            registrar.registerAllWorkers();

            verify(dispatcher).registerWorker(worker);
            verify(dispatcher).workerCount();
        }

        @Test
        @DisplayName("注册顺序与列表顺序一致")
        void should_registerInOrder_when_multipleWorkers() {
            WorkerAgent first = mockWorker(AgentRole.MONITOR);
            WorkerAgent second = mockWorker(AgentRole.ANALYZE);
            WorkerAgent third = mockWorker(AgentRole.EXECUTE);

            WorkerRegistrar registrar = new WorkerRegistrar(dispatcher,
                    List.of(first, second, third));

            registrar.registerAllWorkers();

            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(dispatcher);
            inOrder.verify(dispatcher).registerWorker(first);
            inOrder.verify(dispatcher).registerWorker(second);
            inOrder.verify(dispatcher).registerWorker(third);
        }
    }
}
