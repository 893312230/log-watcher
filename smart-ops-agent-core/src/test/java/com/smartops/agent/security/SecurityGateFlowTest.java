package com.smartops.agent.security;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.agent.orchestrator.SupervisorAgent;
import com.smartops.agent.orchestrator.TaskDispatcher;
import com.smartops.agent.worker.ExecuteAgent;
import com.smartops.agent.worker.WorkerAgent;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.exception.SecurityViolationException;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentCard;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.infrastructure.chat.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 安全门全链路集成测试（IT-2 的 Worker 链路部分）。
 *
 * <p>使用真实的 SupervisorAgent + TaskDispatcher + ExecuteAgent + SecurityGate
 * （注册中心为内存实现，ChatService 为 Mock，不涉及真实 LLM/MCP/DB），端到端验证：
 * <ol>
 *   <li>高危操作未经确认：安全违规异常穿透 Worker → Dispatcher → Supervisor 向上传播</li>
 *   <li>人工确认后（ConfirmationContext 置位）：同一操作顺利执行</li>
 *   <li>确认标记清除后：后续高危操作再次被拦截</li>
 * </ol>
 * API 层的令牌签发/验证部分见 {@code AgentControllerTest.ConfirmationFlow}。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class SecurityGateFlowTest {

    private SupervisorAgent supervisorAgent;

    @BeforeEach
    void setUp() {
        AgentCardRegistry registry = new AgentCardRegistry();
        TaskDispatcher dispatcher = new TaskDispatcher();
        SecurityGate securityGate = new SecurityGate();
        ChatService chatService = mock(ChatService.class);
        when(chatService.chat(anyString(), anyString(), anyString(), any(Object[].class)))
                .thenReturn("操作方案：已执行运维操作（模拟）");
        ExecuteAgent executeAgent = new ExecuteAgent(registry, securityGate, chatService);
        dispatcher.registerWorker(executeAgent);
        // 注册 MONITOR 桩 Worker：只读任务链路下监控子任务可正常返回，
        // 使"全部子任务失败 → success=false"的新语义不影响安全门断言
        WorkerAgent monitorStub = mock(WorkerAgent.class);
        when(monitorStub.getCard()).thenReturn(new AgentCard(
                "monitor-stub", AgentRole.MONITOR, "监控桩", "测试桩",
                Set.of(), Set.of(), 1));
        when(monitorStub.handle(any())).thenAnswer(invocation -> {
            var req = invocation.getArgument(0, com.smartops.common.model.A2aRequest.class);
            return A2aResponse.success(req.requestId(), req.taskId(), AgentRole.MONITOR, "监控正常");
        });
        dispatcher.registerWorker(monitorStub);
        supervisorAgent = new SupervisorAgent(dispatcher, registry);
    }

    @AfterEach
    void tearDown() {
        ConfirmationContext.clear();
    }

    @Test
    @DisplayName("高危操作未经确认：异常穿透 Supervisor 向上传播")
    void should_propagateSecurityViolation_when_highRiskNotConfirmed() {
        assertThatThrownBy(() -> supervisorAgent.orchestrate("重启订单服务", "conv-it1"))
                .isInstanceOf(SecurityViolationException.class)
                .hasMessageContaining("人工确认");
    }

    @Test
    @DisplayName("人工确认后高危操作执行成功")
    void should_executeSuccessfully_when_confirmed() {
        ConfirmationContext.markConfirmed();

        AgentExecutionResult result = supervisorAgent.orchestrate("重启订单服务", "conv-it2");

        assertThat(result.success()).isTrue();
        assertThat(result.answer()).contains("已执行运维操作");
    }

    @Test
    @DisplayName("确认标记清除后后续高危操作再次被拦截")
    void should_rejectAgain_when_confirmationCleared() {
        ConfirmationContext.markConfirmed();
        supervisorAgent.orchestrate("重启订单服务", "conv-it3");
        ConfirmationContext.clear();

        assertThatThrownBy(() -> supervisorAgent.orchestrate("重启订单服务", "conv-it3"))
                .isInstanceOf(SecurityViolationException.class);
    }

    @Test
    @DisplayName("不含高危关键词的任务不触发安全门")
    void should_notTriggerGate_when_readOnlyTask() {
        // 纯监控类任务：不分解出 EXECUTE 子任务，安全门不参与
        AgentExecutionResult result = supervisorAgent.orchestrate("查询 CPU 指标", "conv-it4");

        assertThat(result.success()).isTrue();
    }
}
