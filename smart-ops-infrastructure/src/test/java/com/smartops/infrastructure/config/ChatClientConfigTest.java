package com.smartops.infrastructure.config;

import com.smartops.infrastructure.advisor.BoundedToolExecutionEligibilityChecker;
import com.smartops.infrastructure.observability.Observability;
import com.smartops.infrastructure.observability.ObservedToolCallingManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ChatClientConfig} 单元测试。
 *
 * <p>验证 ChatClient Bean 的构建逻辑：系统提示词加载、Advisor 注入
 * （记忆 Advisor + 挂接有界资格检查器的 ToolCallingAdvisor）。
 * 通过 Mock ChatClient.Builder 和 ChatMemory 避免真实 LLM 调用。
 * 对应 agent.md 阶段一任务2。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ChatClientConfigTest {

    private ChatClientConfig config;
    private ChatClient.Builder builder;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient chatClient;
    private ChatMemory chatMemory;
    private BoundedToolExecutionEligibilityChecker eligibilityChecker;
    private ToolCallingManager toolCallingManager;

    @BeforeEach
    void setUp() throws IOException {
        config = new ChatClientConfig();

        // 注入模拟的系统提示词 Resource
        ByteArrayResource mockResource = new ByteArrayResource(
                "你是一个智能运维助手".getBytes(StandardCharsets.UTF_8)
        );
        ReflectionTestUtils.setField(config, "reactSystemPromptResource", mockResource);
        ReflectionTestUtils.setField(config, "maxToolCallRounds", 10);

        // Mock ChatClient.Builder 链式调用
        builder = mock(ChatClient.Builder.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        chatClient = mock(ChatClient.class);
        chatMemory = mock(ChatMemory.class);
        eligibilityChecker = mock(BoundedToolExecutionEligibilityChecker.class);
        toolCallingManager = mock(ToolCallingManager.class);

        when(builder.defaultSystem(org.mockito.ArgumentMatchers.anyString())).thenReturn(builder);
        when(builder.defaultAdvisors(org.mockito.ArgumentMatchers.any(Advisor[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
    }

    @Test
    @DisplayName("chatClient 方法返回非 null 的 ChatClient 实例")
    void should_returnNonNullChatClient_when_buildCalled() throws IOException {
        ChatClient result = config.chatClient(builder, chatMemory, eligibilityChecker, toolCallingManager);

        assertThat(result).isNotNull().isSameAs(chatClient);
    }

    @Test
    @DisplayName("构建时调用了 Builder 的 defaultSystem 方法注入系统提示词")
    void should_injectSystemPrompt_when_buildCalled() throws IOException {
        config.chatClient(builder, chatMemory, eligibilityChecker, toolCallingManager);

        org.mockito.Mockito.verify(builder).defaultSystem(org.mockito.ArgumentMatchers.contains("智能运维助手"));
    }

    @Test
    @DisplayName("构建时调用了 Builder 的 build 方法")
    void should_callBuild_when_buildCalled() throws IOException {
        config.chatClient(builder, chatMemory, eligibilityChecker, toolCallingManager);

        org.mockito.Mockito.verify(builder).build();
    }

    @Test
    @DisplayName("默认 Advisor 链包含记忆 Advisor 与有界 ToolCallingAdvisor")
    void should_registerMemoryAndBoundedToolAdvisors_when_buildCalled() throws IOException {
        config.chatClient(builder, chatMemory, eligibilityChecker, toolCallingManager);

        ArgumentCaptor<Advisor[]> advisorCaptor = ArgumentCaptor.forClass(Advisor[].class);
        org.mockito.Mockito.verify(builder).defaultAdvisors(advisorCaptor.capture());

        assertThat(advisorCaptor.getValue()).hasSize(2);
        assertThat(advisorCaptor.getValue())
                .anySatisfy(advisor -> assertThat(advisor).isInstanceOf(ToolCallingAdvisor.class));
    }

    @Test
    @DisplayName("boundedToolExecutionEligibilityChecker Bean 使用配置的上限值")
    void should_createCheckerWithConfiguredCap_when_beanMethodCalled() {
        ReflectionTestUtils.setField(config, "maxToolCallRounds", 6);

        BoundedToolExecutionEligibilityChecker checker = config.boundedToolExecutionEligibilityChecker();

        assertThat(checker.getDefaultMaxRounds()).isEqualTo(6);
    }

    @Test
    @DisplayName("toolCallingManager Bean 返回 ObservedToolCallingManager 包装默认委托")
    void should_wrapDefaultManager_when_toolCallingManagerBeanCalled() {
        ToolCallingManager manager = config.toolCallingManager(providerOf(mock(Observability.class)));

        assertThat(manager).isInstanceOf(ObservedToolCallingManager.class);
    }

    @Test
    @DisplayName("toolCallingManager Bean 在 Observability 缺失时仍可构建")
    void should_buildWithoutObservability_when_providerEmpty() {
        ToolCallingManager manager = config.toolCallingManager(providerOf(null));

        assertThat(manager).isInstanceOf(ObservedToolCallingManager.class);
    }

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return instance;
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }
        };
    }
}
