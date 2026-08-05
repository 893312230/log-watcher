package com.smartops.agent.worker;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.agent.security.SecurityGate;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.IntentType;
import com.smartops.common.enums.TaskStatus;
import com.smartops.common.exception.SecurityViolationException;
import com.smartops.common.model.A2aRequest;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentCard;
import com.smartops.infrastructure.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExecuteAgent} 单元测试。
 *
 * <p>验证执行 Agent 的核心契约：
 * <ul>
 *   <li>构造时向 {@link AgentCardRegistry} 注册能力卡片</li>
 *   <li>安全门先于 LLM 调用，未确认时安全违规异常向上传播</li>
 *   <li>安全门放行后由 LLM 生成操作方案（当前阶段实际执行为模拟）</li>
 *   <li>角色不匹配时返回失败响应</li>
 * </ul></p>
 *
 * <p>对应 agent.md 阶段三执行 Agent（S7：Worker 接入真实 LLM）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ExecuteAgentTest {

    private AgentCardRegistry registry;
    private SecurityGate securityGate;
    private ChatService chatService;
    private ExecuteAgent agent;

    @BeforeEach
    void setUp() {
        registry = mock(AgentCardRegistry.class);
        securityGate = mock(SecurityGate.class);
        chatService = mock(ChatService.class);
        agent = new ExecuteAgent(registry, securityGate, chatService);
    }

    /**
     * 构造目标角色为 EXECUTE 的 A2A 请求。
     *
     * @return 匹配的请求
     */
    private A2aRequest executeRequest() {
        return new A2aRequest("req-e-001", "task-e-001", AgentRole.SUPERVISOR,
                AgentRole.EXECUTE, "重启订单服务", "conv-e-001");
    }

    @Nested
    @DisplayName("构造与能力卡片")
    class Construction {

        @Test
        @DisplayName("构造时向注册中心注册能力卡片")
        void should_registerCard_when_constructed() {
            verify(registry).register(agent.getCard());
        }

        @Test
        @DisplayName("能力卡片包含正确的 agentId、角色与意图")
        void should_haveCorrectCard_when_constructed() {
            AgentCard card = agent.getCard();

            assertThat(card.agentId()).isEqualTo("execute-agent");
            assertThat(card.role()).isEqualTo(AgentRole.EXECUTE);
            assertThat(card.name()).isEqualTo("执行Agent");
            assertThat(card.description()).contains("运维操作");
            assertThat(card.supportedIntents())
                    .containsExactlyInAnyOrder(IntentType.EXECUTE_OPERATION);
            assertThat(card.maxConcurrency()).isEqualTo(2);
            assertThat(card.expertise()).contains("restart", "scaling");
        }
    }

    @Nested
    @DisplayName("handle 请求处理")
    class Handle {

        @Test
        @DisplayName("安全门放行后返回 LLM 生成的操作方案")
        void should_returnLlmPlan_when_gatePermits() {
            when(chatService.chat(eq("conv-e-001"), any(String.class), eq("重启订单服务"),
                    any(Object[].class))).thenReturn("操作方案：1. 摘流量 2. 重启 3. 恢复");
            A2aRequest request = executeRequest();

            A2aResponse response = agent.handle(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(response.sourceRole()).isEqualTo(AgentRole.EXECUTE);
            assertThat(response.result()).contains("操作方案");
            assertThat(response.requestId()).isEqualTo(request.requestId());
            assertThat(response.taskId()).isEqualTo(request.taskId());
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("目标角色不匹配时返回失败响应")
        void should_returnFailure_when_targetRoleMismatch() {
            A2aRequest request = new A2aRequest("req-e-002", "task-e-002", AgentRole.SUPERVISOR,
                    AgentRole.MONITOR, "重启订单服务", "conv-e-002");

            A2aResponse response = agent.handle(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(response.error()).contains("目标角色不匹配");
        }

        @Test
        @DisplayName("request 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_requestIsNull() {
            assertThatThrownBy(() -> agent.handle(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("request");
        }
    }

    @Nested
    @DisplayName("安全门校验")
    class SecurityGateCheck {

        @Test
        @DisplayName("执行指令前调用安全门校验")
        void should_checkSecurityGate_when_handlingRequest() {
            when(chatService.chat(anyString(), anyString(), anyString(), any(Object[].class)))
                    .thenReturn("方案");
            A2aRequest request = executeRequest();

            agent.handle(request);

            verify(securityGate).checkPermitted(request.instruction());
        }

        @Test
        @DisplayName("安全门拒绝时不调用 LLM 且安全违规异常向上传播")
        void should_propagateSecurityViolationAndSkipLlm_when_gateRejects() {
            doThrow(new SecurityViolationException("SECURITY_CONFIRM_REQUIRED", "高风险操作需要人工确认"))
                    .when(securityGate).checkPermitted(anyString());
            A2aRequest request = executeRequest();

            assertThatThrownBy(() -> agent.handle(request))
                    .isInstanceOf(SecurityViolationException.class)
                    .hasMessageContaining("高风险操作需要人工确认");
            verify(chatService, never()).chat(anyString(), anyString(), anyString(), any(Object[].class));
        }
    }
}
