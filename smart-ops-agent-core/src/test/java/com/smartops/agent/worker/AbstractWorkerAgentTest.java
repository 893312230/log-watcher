package com.smartops.agent.worker;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.IntentType;
import com.smartops.common.enums.TaskStatus;
import com.smartops.common.exception.LlmCallException;
import com.smartops.common.model.A2aRequest;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentCard;
import com.smartops.infrastructure.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AbstractWorkerAgent} 单元测试。
 *
 * <p>验证 Worker 抽象基类的模板方法模式核心契约：
 * <ul>
 *   <li>构造时自动向 {@link AgentCardRegistry} 注册能力卡片</li>
 *   <li>请求校验：null 检查、目标角色匹配</li>
 *   <li>异常捕获：doHandle 抛出异常时转为失败响应</li>
 *   <li>正常流程：委托 doHandle 并返回子类响应</li>
 *   <li>getCard / getRegistry 访问器</li>
 * </ul></p>
 *
 * <p>对应 agent.md 阶段三 Worker 抽象基类。</p>
 *
 * <p><b>测试策略</b>：
 * <ul>
 *   <li>创建匿名子类实现 doHandle，测试模板方法模式</li>
 *   <li>Mock {@link AgentCardRegistry} 验证注册调用</li>
 *   <li>使用 AssertJ 断言，遵循 Arrange-Act-Assert 三段式</li>
 *   <li>使用 {@code @Nested} 分组，{@code @DisplayName} 中文描述</li>
 *   <li>测试方法命名 {@code should_{期望行为}_when_{前置条件}}</li>
 * </ul></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AbstractWorkerAgentTest {

    private AgentCardRegistry registry;
    private ChatService chatService;
    private AgentCard card;

    @BeforeEach
    void setUp() {
        registry = mock(AgentCardRegistry.class);
        chatService = mock(ChatService.class);
        card = new AgentCard(
                "test-worker", AgentRole.MONITOR, "测试Worker",
                "测试用途",
                Set.of("test"),
                Set.of(IntentType.QUERY_METRIC),
                3);
    }

    /**
     * 创建匿名子类，doHandle 正常返回成功响应。
     *
     * @param workerCard 能力卡片
     * @param workerRegistry 注册中心
     * @return 匿子类实例
     */
    private AbstractWorkerAgent createNormalAgent(AgentCard workerCard, AgentCardRegistry workerRegistry) {
        return new AbstractWorkerAgent(workerCard, workerRegistry, chatService) {
            @Override
            protected A2aResponse doHandle(A2aRequest request) {
                return A2aResponse.success(request.requestId(), request.taskId(),
                        workerCard.role(), "处理完成: " + request.instruction());
            }
        };
    }

    /**
     * 创建匿名子类，doHandle 抛出异常，用于测试异常捕获逻辑。
     *
     * @param workerCard 能力卡片
     * @param workerRegistry 注册中心
     * @return 抛异常的匿名子类实例
     */
    private AbstractWorkerAgent createFailingAgent(AgentCard workerCard, AgentCardRegistry workerRegistry) {
        return new AbstractWorkerAgent(workerCard, workerRegistry, chatService) {
            @Override
            protected A2aResponse doHandle(A2aRequest request) {
                throw new LlmCallException("业务处理异常");
            }
        };
    }

    /**
     * 构造目标角色匹配的 A2A 请求（targetRole=MONITOR）。
     *
     * @return 匹配的请求
     */
    private A2aRequest matchingRequest() {
        return new A2aRequest("req-001", "task-001", AgentRole.SUPERVISOR,
                AgentRole.MONITOR, "查询CPU指标", "conv-001");
    }

    /**
     * 构造目标角色不匹配的 A2A 请求（targetRole=ANALYZE）。
     *
     * @return 不匹配的请求
     */
    private A2aRequest mismatchRequest() {
        return new A2aRequest("req-002", "task-002", AgentRole.SUPERVISOR,
                AgentRole.ANALYZE, "查询CPU指标", "conv-002");
    }

    @Nested
    @DisplayName("构造与初始化")
    class Construction {

        @Test
        @DisplayName("构造时自动向注册中心注册卡片")
        void should_registerCard_when_constructed() {
            AbstractWorkerAgent agent = createNormalAgent(card, registry);

            verify(registry).register(card);
        }

        @Test
        @DisplayName("构造时 card 为 null 抛出 NullPointerException")
        void should_throwNpe_when_cardIsNull() {
            assertThatThrownBy(() -> createNormalAgent(null, registry))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("card");
        }

        @Test
        @DisplayName("构造时 registry 为 null 抛出 NullPointerException")
        void should_throwNpe_when_registryIsNull() {
            assertThatThrownBy(() -> createNormalAgent(card, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("registry");
        }

        @Test
        @DisplayName("getCard 返回构造时传入的卡片")
        void should_returnCard_when_getCardInvoked() {
            AbstractWorkerAgent agent = createNormalAgent(card, registry);

            assertThat(agent.getCard()).isEqualTo(card);
            assertThat(agent.getCard().agentId()).isEqualTo("test-worker");
            assertThat(agent.getCard().role()).isEqualTo(AgentRole.MONITOR);
        }

        @Test
        @DisplayName("getRegistry 返回构造时传入的注册中心")
        void should_returnRegistry_when_getRegistryInvoked() {
            AbstractWorkerAgent agent = createNormalAgent(card, registry);

            assertThat(agent.getRegistry()).isSameAs(registry);
        }
    }

    @Nested
    @DisplayName("handle 请求处理 - 正常流程")
    class HandleNormal {

        @Test
        @DisplayName("目标角色匹配时调用 doHandle 并返回成功响应")
        void should_callDoHandle_when_targetRoleMatches() {
            AbstractWorkerAgent agent = createNormalAgent(card, registry);
            A2aRequest request = matchingRequest();

            A2aResponse response = agent.handle(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(response.sourceRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(response.result()).contains("处理完成");
            assertThat(response.result()).contains(request.instruction());
            assertThat(response.requestId()).isEqualTo(request.requestId());
            assertThat(response.taskId()).isEqualTo(request.taskId());
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("doHandle 返回的响应原样透传")
        void should_passThroughResponse_when_doHandleReturns() {
            AbstractWorkerAgent agent = createNormalAgent(card, registry);
            A2aRequest request = matchingRequest();

            A2aResponse response = agent.handle(request);

            assertThat(response.result()).startsWith("处理完成");
            assertThat(response.sourceRole()).isEqualTo(AgentRole.MONITOR);
        }
    }

    @Nested
    @DisplayName("handle 请求处理 - 异常处理")
    class HandleException {

        @Test
        @DisplayName("目标角色不匹配时返回失败响应，不调用 doHandle")
        void should_returnFailure_when_targetRoleMismatch() {
            AbstractWorkerAgent agent = createNormalAgent(card, registry);
            A2aRequest request = mismatchRequest();

            A2aResponse response = agent.handle(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(response.sourceRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(response.error()).contains("目标角色不匹配");
            assertThat(response.error()).contains("MONITOR");
            assertThat(response.error()).contains("ANALYZE");
            assertThat(response.requestId()).isEqualTo(request.requestId());
            assertThat(response.taskId()).isEqualTo(request.taskId());
        }

        @Test
        @DisplayName("request 为 null 时抛出 NullPointerException")
        void should_throwNpe_when_requestIsNull() {
            AbstractWorkerAgent agent = createNormalAgent(card, registry);

            assertThatThrownBy(() -> agent.handle(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("request");
        }

        @Test
        @DisplayName("doHandle 抛出平台异常时捕获并返回失败响应")
        void should_returnFailure_when_doHandleThrowsAgentException() {
            AbstractWorkerAgent agent = createFailingAgent(card, registry);
            A2aRequest request = matchingRequest();

            A2aResponse response = agent.handle(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(response.error()).contains("Worker 内部错误");
            assertThat(response.error()).contains("业务处理异常");
            assertThat(response.requestId()).isEqualTo(request.requestId());
            assertThat(response.taskId()).isEqualTo(request.taskId());
            assertThat(response.sourceRole()).isEqualTo(AgentRole.MONITOR);
        }

        @Test
        @DisplayName("doHandle 抛出非平台异常（编程错误）时向上传播，不吞为失败响应")
        void should_propagate_when_doHandleThrowsNonPlatformException() {
            AbstractWorkerAgent agent = new AbstractWorkerAgent(card, registry, chatService) {
                @Override
                protected A2aResponse doHandle(A2aRequest request) {
                    throw new IllegalArgumentException("参数不合法");
                }
            };
            A2aRequest request = matchingRequest();

            assertThatThrownBy(() -> agent.handle(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("参数不合法");
        }

        @Test
        @DisplayName("doHandle 抛出 SecurityViolationException 时向上传播（不转为失败响应）")
        void should_propagateSecurityViolation_when_doHandleThrowsIt() {
            AbstractWorkerAgent agent = new AbstractWorkerAgent(card, registry, chatService) {
                @Override
                protected A2aResponse doHandle(A2aRequest request) {
                    throw new com.smartops.common.exception.SecurityViolationException(
                            "SECURITY_CONFIRM_REQUIRED", "高风险操作需要人工确认");
                }
            };
            A2aRequest request = matchingRequest();

            assertThatThrownBy(() -> agent.handle(request))
                    .isInstanceOf(com.smartops.common.exception.SecurityViolationException.class)
                    .hasMessageContaining("高风险操作需要人工确认");
        }
    }

    @Nested
    @DisplayName("chatWithRolePrompt 会话路由")
    class ChatWithRolePrompt {

        /**
         * 创建暴露 chatWithRolePrompt 的匿名子类。
         *
         * @return 匿名子类实例
         */
        private AbstractWorkerAgent createChatAgent() {
            return new AbstractWorkerAgent(card, registry, chatService) {
                @Override
                protected A2aResponse doHandle(A2aRequest request) {
                    String answer = chatWithRolePrompt("系统提示", request);
                    return A2aResponse.success(request.requestId(), request.taskId(),
                            card.role(), answer);
                }
            };
        }

        @Test
        @DisplayName("请求携带会话 ID 时走会话级调用")
        void should_useConversationChat_when_conversationIdPresent() {
            when(chatService.chat(eq("conv-001"), eq("系统提示"), eq("查询CPU指标"), any(Object[].class)))
                    .thenReturn("会话回答");
            AbstractWorkerAgent agent = createChatAgent();

            A2aResponse response = agent.handle(matchingRequest());

            assertThat(response.result()).isEqualTo("会话回答");
            verify(chatService).chat(eq("conv-001"), eq("系统提示"), eq("查询CPU指标"), any(Object[].class));
        }

        @Test
        @DisplayName("请求会话 ID 为空白时降级为无状态调用")
        void should_useStatelessChat_when_conversationIdBlank() {
            when(chatService.chatWithSystemPrompt(eq("系统提示"), eq("查询CPU指标"), any(Object[].class)))
                    .thenReturn("无状态回答");
            AbstractWorkerAgent agent = createChatAgent();
            A2aRequest request = new A2aRequest("req-003", "task-003", AgentRole.SUPERVISOR,
                    AgentRole.MONITOR, "查询CPU指标", " ");

            A2aResponse response = agent.handle(request);

            assertThat(response.result()).isEqualTo("无状态回答");
            verify(chatService).chatWithSystemPrompt(eq("系统提示"), eq("查询CPU指标"), any(Object[].class));
        }
    }
}
