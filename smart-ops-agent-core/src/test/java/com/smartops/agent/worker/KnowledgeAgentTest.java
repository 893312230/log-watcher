package com.smartops.agent.worker;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.IntentType;
import com.smartops.common.enums.TaskStatus;
import com.smartops.common.model.A2aRequest;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentCard;
import com.smartops.domain.knowledge.KnowledgeChunk;
import com.smartops.domain.knowledge.KnowledgeRetriever;
import com.smartops.infrastructure.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeAgent} 单元测试。
 *
 * <p>验证知识 Agent 的核心契约：
 * <ul>
 *   <li>构造时向 {@link AgentCardRegistry} 注册能力卡片</li>
 *   <li>检索器缺失/检索为空/检索异常时降级：LLM 回答附"知识库未接入"声明前缀</li>
 *   <li>检索命中时 RAG 路径：上下文注入系统提示词、回答不带前缀</li>
 *   <li>角色不匹配时返回失败响应</li>
 * </ul></p>
 *
 * <p>对应 agent.md 阶段三知识 Agent、阶段四 RAG 接入（ADR-016）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class KnowledgeAgentTest {

    private AgentCardRegistry registry;
    private ChatService chatService;
    private ObjectProvider<KnowledgeRetriever> retrieverProvider;
    private KnowledgeRetriever knowledgeRetriever;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = mock(AgentCardRegistry.class);
        chatService = mock(ChatService.class);
        retrieverProvider = mock(ObjectProvider.class);
        knowledgeRetriever = mock(KnowledgeRetriever.class);
    }

    /**
     * 构造检索器可用的 KnowledgeAgent。
     *
     * @return Agent 实例
     */
    private KnowledgeAgent agentWithRetriever() {
        when(retrieverProvider.getIfAvailable()).thenReturn(knowledgeRetriever);
        return new KnowledgeAgent(registry, chatService, retrieverProvider, 5);
    }

    /**
     * 构造检索器缺失的 KnowledgeAgent。
     *
     * @return Agent 实例
     */
    private KnowledgeAgent agentWithoutRetriever() {
        when(retrieverProvider.getIfAvailable()).thenReturn(null);
        return new KnowledgeAgent(registry, chatService, retrieverProvider, 5);
    }

    /**
     * 构造目标角色为 KNOWLEDGE 的 A2A 请求。
     *
     * @return 匹配的请求
     */
    private A2aRequest knowledgeRequest() {
        return new A2aRequest("req-k-001", "task-k-001", AgentRole.SUPERVISOR,
                AgentRole.KNOWLEDGE, "Nginx负载均衡最佳实践", "conv-k-001");
    }

    @Nested
    @DisplayName("构造与能力卡片")
    class Construction {

        @Test
        @DisplayName("构造时向注册中心注册能力卡片")
        void should_registerCard_when_constructed() {
            KnowledgeAgent agent = agentWithoutRetriever();

            verify(registry).register(agent.getCard());
        }

        @Test
        @DisplayName("能力卡片包含正确的 agentId、角色与意图")
        void should_haveCorrectCard_when_constructed() {
            AgentCard card = agentWithoutRetriever().getCard();

            assertThat(card.agentId()).isEqualTo("knowledge-agent");
            assertThat(card.role()).isEqualTo(AgentRole.KNOWLEDGE);
            assertThat(card.name()).isEqualTo("知识Agent");
            assertThat(card.description()).contains("知识库");
            assertThat(card.supportedIntents())
                    .containsExactlyInAnyOrder(IntentType.KNOWLEDGE_QA);
            assertThat(card.maxConcurrency()).isEqualTo(5);
            assertThat(card.expertise()).contains("knowledge-base", "best-practices");
        }
    }

    @Nested
    @DisplayName("降级路径（无检索结果）")
    class DegradedPath {

        @Test
        @DisplayName("检索器缺失时返回 LLM 回答并附未接入声明前缀")
        void should_returnPrefixedAnswer_when_retrieverMissing() {
            KnowledgeAgent agent = agentWithoutRetriever();
            when(chatService.chat(eq("conv-k-001"), any(String.class), eq("Nginx负载均衡最佳实践"),
                    any(Object[].class))).thenReturn("推荐使用轮询或最少连接算法");

            A2aResponse response = agent.handle(knowledgeRequest());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.result()).startsWith(KnowledgeAgent.KNOWLEDGE_NOT_READY_PREFIX);
            assertThat(response.result()).contains("最少连接算法");
        }

        @Test
        @DisplayName("检索为空时返回 LLM 回答并附前缀")
        void should_returnPrefixedAnswer_when_retrievalEmpty() {
            KnowledgeAgent agent = agentWithRetriever();
            when(knowledgeRetriever.retrieve(anyString(), anyInt())).thenReturn(List.of());
            when(chatService.chat(any(String.class), any(String.class), any(String.class),
                    any(Object[].class))).thenReturn("通用回答");

            A2aResponse response = agent.handle(knowledgeRequest());

            assertThat(response.result()).startsWith(KnowledgeAgent.KNOWLEDGE_NOT_READY_PREFIX);
        }

        @Test
        @DisplayName("检索器抛异常时降级并附前缀")
        void should_returnPrefixedAnswer_when_retrieverThrows() {
            KnowledgeAgent agent = agentWithRetriever();
            when(knowledgeRetriever.retrieve(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("ES 不可用"));
            when(chatService.chat(any(String.class), any(String.class), any(String.class),
                    any(Object[].class))).thenReturn("通用回答");

            A2aResponse response = agent.handle(knowledgeRequest());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.result()).startsWith(KnowledgeAgent.KNOWLEDGE_NOT_READY_PREFIX);
        }
    }

    @Nested
    @DisplayName("RAG 路径（检索命中）")
    class RagPath {

        @Test
        @DisplayName("检索命中时上下文注入系统提示词且回答不带前缀")
        void should_injectContext_when_retrievalHits() {
            KnowledgeAgent agent = agentWithRetriever();
            when(knowledgeRetriever.retrieve("Nginx负载均衡最佳实践", 5)).thenReturn(List.of(
                    new KnowledgeChunk("id-1", "轮询适合无状态服务", "runbooks/nginx.md", "负载均衡", 0.5)));
            when(chatService.chat(any(String.class), any(String.class), any(String.class),
                    any(Object[].class))).thenReturn("依据知识库：推荐轮询");

            A2aResponse response = agent.handle(knowledgeRequest());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.result()).isEqualTo("依据知识库：推荐轮询");
            assertThat(response.result()).doesNotContain("知识库尚未接入");

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(chatService).chat(eq("conv-k-001"), promptCaptor.capture(),
                    eq("Nginx负载均衡最佳实践"), any(Object[].class));
            String systemPrompt = promptCaptor.getValue();
            assertThat(systemPrompt).contains("【知识库检索结果】");
            assertThat(systemPrompt).contains("runbooks/nginx.md");
            assertThat(systemPrompt).contains("负载均衡");
            assertThat(systemPrompt).contains("轮询适合无状态服务");
        }
    }

    @Nested
    @DisplayName("handle 请求处理")
    class Handle {

        @Test
        @DisplayName("调用 LLM 时携带会话 ID 与原始指令")
        void should_callLlmWithConversation_when_handle() {
            KnowledgeAgent agent = agentWithoutRetriever();
            when(chatService.chat(any(String.class), any(String.class), any(String.class),
                    any(Object[].class))).thenReturn("回答");
            A2aRequest request = knowledgeRequest();

            agent.handle(request);

            verify(chatService).chat(eq("conv-k-001"), any(String.class),
                    eq("Nginx负载均衡最佳实践"), any(Object[].class));
        }

        @Test
        @DisplayName("目标角色不匹配时返回失败响应")
        void should_returnFailure_when_targetRoleMismatch() {
            KnowledgeAgent agent = agentWithoutRetriever();
            A2aRequest request = new A2aRequest("req-k-002", "task-k-002", AgentRole.SUPERVISOR,
                    AgentRole.MONITOR, "Nginx负载均衡最佳实践", "conv-k-002");

            A2aResponse response = agent.handle(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(response.error()).contains("目标角色不匹配");
        }

        @Test
        @DisplayName("request 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_requestIsNull() {
            assertThatThrownBy(() -> agentWithoutRetriever().handle(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("request");
        }
    }
}
