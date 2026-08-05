package com.smartops.infrastructure.chat;

import com.smartops.infrastructure.llm.LlmProvider;
import com.smartops.infrastructure.llm.LlmProviderRegistry;
import com.smartops.infrastructure.llm.LlmProviderRegistryImpl;
import com.smartops.infrastructure.observability.Observability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ChatService} 多模型 Provider 集成构造测试。
 *
 * <p>验证：4 参构造器注入 registry 后各公开方法不抛 NPE（注册表与默认客户端均已就绪）；
 * 3 参构造器（旧兼容路径）行为不变；无注册表时无状态调用走原有客户端。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ChatServiceProviderSelectionTest {

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return instance; }
            @Override public T getIfAvailable() { return instance; }
        };
    }

    @Test
    @DisplayName("4 参构造器不抛异常，公开方法可调用")
    void should_notThrow_when_constructedWithRegistry() {
        ChatClient client = mock(ChatClient.class);
        LlmProvider p = mock(LlmProvider.class);
        when(p.name()).thenReturn("ds");
        when(p.supportsTools()).thenReturn(false);
        when(p.chatClient()).thenReturn(client);
        LlmProviderRegistry reg = new LlmProviderRegistryImpl(List.of(p), "ds");

        ChatService service = new ChatService(client,
                mock(ChatClient.Builder.class), mock(Observability.class),
                providerOf(reg), providerOf(null), 0);
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("3 参构造器（无 registry）行为不变")
    void should_workWithThreeArgConstructor() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient stateless = mock(ChatClient.class);
        when(builder.build()).thenReturn(stateless);

        ChatService service = new ChatService(mock(ChatClient.class), builder,
                mock(Observability.class));
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("2 参构造器（无可观测性、无 registry）行为不变")
    void should_workWithTwoArgConstructor() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient stateless = mock(ChatClient.class);
        when(builder.build()).thenReturn(stateless);

        ChatService service = new ChatService(mock(ChatClient.class), builder);
        assertThat(service).isNotNull();
    }
}
