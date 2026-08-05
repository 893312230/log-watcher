package com.smartops.agent.worker;

import com.smartops.agent.a2a.AgentCardRegistry;
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
 * {@link AnalyzeAgent} 单元测试。
 *
 * <p>验证分析 Agent 的核心契约：
 * <ul>
 *   <li>构造时向 {@link AgentCardRegistry} 注册能力卡片</li>
 *   <li>能力卡片声明正确的角色、意图、并发数</li>
 *   <li>handle 经 LLM 上下文分析生成结构化分析报告</li>
 *   <li>角色不匹配时返回失败响应</li>
 * </ul></p>
 *
 * <p>对应 agent.md 阶段三分析 Agent（S7：Worker 接入真实 LLM）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AnalyzeAgentTest {

    private AgentCardRegistry registry;
    private ChatService chatService;
    private AnalyzeAgent agent;

    @BeforeEach
    void setUp() {
        registry = mock(AgentCardRegistry.class);
        chatService = mock(ChatService.class);
        agent = new AnalyzeAgent(registry, chatService);
    }

    /**
     * 构造目标角色为 ANALYZE 的 A2A 请求。
     *
     * @return 匹配的请求
     */
    private A2aRequest analyzeRequest() {
        return new A2aRequest("req-a-001", "task-a-001", AgentRole.SUPERVISOR,
                AgentRole.ANALYZE, "分析服务响应变慢根因", "conv-a-001");
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

            assertThat(card.agentId()).isEqualTo("analyze-agent");
            assertThat(card.role()).isEqualTo(AgentRole.ANALYZE);
            assertThat(card.name()).isEqualTo("分析Agent");
            assertThat(card.description()).contains("根因分析");
            assertThat(card.supportedIntents())
                    .containsExactlyInAnyOrder(IntentType.ROOT_CAUSE, IntentType.ANALYZE_ALERT);
            assertThat(card.maxConcurrency()).isEqualTo(3);
            assertThat(card.expertise()).contains("root-cause", "logs");
        }
    }

    @Nested
    @DisplayName("handle 请求处理")
    class Handle {

        @Test
        @DisplayName("处理分析指令返回 LLM 生成的分析报告")
        void should_returnLlmAnswer_when_handleAnalyzeRequest() {
            when(chatService.chat(eq("conv-a-001"), any(String.class), eq("分析服务响应变慢根因"),
                    any(Object[].class))).thenReturn("现象：响应变慢。可能原因：连接池耗尽。");
            A2aRequest request = analyzeRequest();

            A2aResponse response = agent.handle(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(response.sourceRole()).isEqualTo(AgentRole.ANALYZE);
            assertThat(response.result()).contains("连接池耗尽");
            assertThat(response.requestId()).isEqualTo(request.requestId());
            assertThat(response.taskId()).isEqualTo(request.taskId());
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("调用 LLM 时携带会话 ID 与原始指令")
        void should_callLlmWithConversation_when_handle() {
            when(chatService.chat(any(String.class), any(String.class), any(String.class),
                    any(Object[].class))).thenReturn("报告");
            A2aRequest request = analyzeRequest();

            agent.handle(request);

            verify(chatService).chat(eq("conv-a-001"), any(String.class),
                    eq("分析服务响应变慢根因"), any(Object[].class));
        }

        @Test
        @DisplayName("目标角色不匹配时返回失败响应")
        void should_returnFailure_when_targetRoleMismatch() {
            A2aRequest request = new A2aRequest("req-a-002", "task-a-002", AgentRole.SUPERVISOR,
                    AgentRole.MONITOR, "分析服务响应变慢根因", "conv-a-002");

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
