package com.smartops.agent.worker;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.agent.tools.PrometheusTools;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.IntentType;
import com.smartops.common.enums.TaskStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MonitorAgent} 单元测试。
 *
 * <p>验证监控 Agent 的核心契约：
 * <ul>
 *   <li>构造时向 {@link AgentCardRegistry} 注册能力卡片</li>
 *   <li>能力卡片声明正确的角色、意图、并发数</li>
 *   <li>handle 经 LLM + PrometheusTools 工具调用生成真实监控结论</li>
 *   <li>角色不匹配时返回失败响应</li>
 *   <li>request 为 null 时抛出 NullPointerException</li>
 * </ul></p>
 *
 * <p>对应 agent.md 阶段三监控 Agent（S7：Worker 接入真实 LLM）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class MonitorAgentTest {

    private AgentCardRegistry registry;
    private PrometheusTools prometheusTools;
    private ChatService chatService;
    private MonitorAgent agent;

    @BeforeEach
    void setUp() {
        registry = mock(AgentCardRegistry.class);
        prometheusTools = mock(PrometheusTools.class);
        chatService = mock(ChatService.class);
        agent = new MonitorAgent(registry, prometheusTools, chatService);
    }

    /**
     * 构造目标角色为 MONITOR 的 A2A 请求。
     *
     * @return 匹配的请求
     */
    private A2aRequest monitorRequest() {
        return new A2aRequest("req-m-001", "task-m-001", AgentRole.SUPERVISOR,
                AgentRole.MONITOR, "查询CPU使用率", "conv-m-001");
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
        @DisplayName("能力卡片包含正确的 agentId 和角色")
        void should_haveCorrectCard_when_constructed() {
            AgentCard card = agent.getCard();

            assertThat(card.agentId()).isEqualTo("monitor-agent");
            assertThat(card.role()).isEqualTo(AgentRole.MONITOR);
            assertThat(card.name()).isEqualTo("监控Agent");
            assertThat(card.description()).contains("监控");
        }

        @Test
        @DisplayName("能力卡片声明支持的意图类型")
        void should_declareSupportedIntents_when_constructed() {
            assertThat(agent.getCard().supportedIntents())
                    .containsExactlyInAnyOrder(
                            IntentType.QUERY_METRIC,
                            IntentType.TREND_ANALYSIS,
                            IntentType.ANALYZE_ALERT);
        }

        @Test
        @DisplayName("能力卡片最大并发数为 5 且声明专长领域")
        void should_haveConcurrencyAndExpertise_when_constructed() {
            AgentCard card = agent.getCard();

            assertThat(card.maxConcurrency()).isEqualTo(5);
            assertThat(card.expertise()).contains("prometheus", "metrics");
        }
    }

    @Nested
    @DisplayName("handle 请求处理")
    class Handle {

        @Test
        @DisplayName("处理监控指令返回 LLM 生成的监控结论")
        void should_returnLlmAnswer_when_handleMonitorRequest() {
            when(chatService.chat(eq("conv-m-001"), any(String.class), eq("查询CPU使用率"),
                    any(Object[].class))).thenReturn("CPU 使用率 42%，处于正常范围");
            A2aRequest request = monitorRequest();

            A2aResponse response = agent.handle(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(response.sourceRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(response.result()).isEqualTo("CPU 使用率 42%，处于正常范围");
            assertThat(response.requestId()).isEqualTo(request.requestId());
            assertThat(response.taskId()).isEqualTo(request.taskId());
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("调用 LLM 时注入 PrometheusTools 工具并携带会话 ID")
        void should_callLlmWithToolsAndConversation_when_handle() {
            when(chatService.chat(any(String.class), any(String.class), any(String.class),
                    any(Object[].class))).thenReturn("结论");
            A2aRequest request = monitorRequest();

            agent.handle(request);

            // varargs 展开后第 4 个实参即 PrometheusTools 本身
            verify(chatService).chat(eq("conv-m-001"), any(String.class), eq("查询CPU使用率"),
                    eq(prometheusTools));
        }

        @Test
        @DisplayName("目标角色不匹配时返回失败响应")
        void should_returnFailure_when_targetRoleMismatch() {
            A2aRequest request = new A2aRequest("req-m-002", "task-m-002", AgentRole.SUPERVISOR,
                    AgentRole.ANALYZE, "查询CPU使用率", "conv-m-002");

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
}
