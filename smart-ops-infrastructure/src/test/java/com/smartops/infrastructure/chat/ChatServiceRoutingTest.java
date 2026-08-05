package com.smartops.infrastructure.chat;

import com.smartops.common.exception.LlmCallException;
import com.smartops.common.exception.RateLimitException;
import com.smartops.infrastructure.llm.LlmProvider;
import com.smartops.infrastructure.llm.LlmProviderRegistry;
import com.smartops.infrastructure.llm.LlmProviderRegistryImpl;
import com.smartops.infrastructure.observability.Observability;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceRoutingTest {

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return instance; }
            @Override public T getIfAvailable() { return instance; }
        };
    }

    @Test
    @DisplayName("registry null → statelessClient 回退原有客户端")
    void should_returnFallbackStateless_when_registryNull() {
        ChatClient stateless = mock(ChatClient.class);
        ChatClient.Builder b = mock(ChatClient.Builder.class);
        when(b.build()).thenReturn(stateless);
        ChatService service = new ChatService(mock(ChatClient.class), b,
                mock(Observability.class));
        assertThat(service.statelessClient(false)).isSameAs(stateless);
        assertThat(service.statelessClient(true)).isSameAs(stateless);
    }

    @Test
    @DisplayName("registry 存在时 statelessClient(true) 选工具 Provider")
    void should_selectToolCapable_when_needsTools() {
        ChatClient toolClient = mock(ChatClient.class);
        LlmProvider pt = mock(LlmProvider.class);
        when(pt.name()).thenReturn("ds");
        when(pt.supportsTools()).thenReturn(true);
        when(pt.chatClient()).thenReturn(toolClient);
        LlmProviderRegistry reg = new LlmProviderRegistryImpl(List.of(pt), "ds");
        ChatService service = new ChatService(mock(ChatClient.class),
                mock(ChatClient.Builder.class), mock(Observability.class),
                providerOf(reg), providerOf(null), 0);
        assertThat(service.statelessClient(true)).isSameAs(toolClient);
    }

    @Test
    @DisplayName("registry 存在时 statelessClient(false) 选默认 Provider")
    void should_selectDefault_when_noTools() {
        ChatClient defaultClient = mock(ChatClient.class);
        LlmProvider p = mock(LlmProvider.class);
        when(p.name()).thenReturn("ds");
        when(p.chatClient()).thenReturn(defaultClient);
        LlmProviderRegistry reg = new LlmProviderRegistryImpl(List.of(p), "ds");
        ChatService service = new ChatService(mock(ChatClient.class),
                mock(ChatClient.Builder.class), mock(Observability.class),
                providerOf(reg), providerOf(null), 0);
        assertThat(service.statelessClient(false)).isSameAs(defaultClient);
    }

    @Test
    @DisplayName("ratePerMinute=0 时不限流")
    void should_notRateLimit_when_ratePerMinuteZero() {
        ChatClient.Builder b = mock(ChatClient.Builder.class);
        when(b.build()).thenReturn(mock(ChatClient.class));
        ChatService service = new ChatService(mock(ChatClient.class), b,
                mock(Observability.class), providerOf(null), providerOf(null), 0);
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("限流启用时 invokeLlm 达限后抛出 RateLimitException")
    void should_rateLimit_when_limitReached() {
        ChatClient.Builder b = mock(ChatClient.Builder.class);
        when(b.build()).thenReturn(mock(ChatClient.class));
        ChatService service = new ChatService(mock(ChatClient.class), b,
                mock(Observability.class), providerOf(null), providerOf(null), 1);
        assertThat(service.invokeLlm(() -> "first-ok")).isEqualTo("first-ok");
        assertThatThrownBy(() -> service.invokeLlm(() -> "second"))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("熔断器打开时 invokeLlm 抛出 LlmCallException")
    void should_throwWhenCircuitOpen() {
        CircuitBreaker cb = mock(CircuitBreaker.class);
        when(cb.tryAcquirePermission()).thenReturn(false);
        ChatClient.Builder b = mock(ChatClient.Builder.class);
        when(b.build()).thenReturn(mock(ChatClient.class));
        ChatService service = new ChatService(mock(ChatClient.class), b,
                mock(Observability.class),
                providerOf((LlmProviderRegistry) null),
                providerOf(cb), 0);

        assertThatThrownBy(() -> service.invokeLlm(() -> "should-not-run"))
                .isInstanceOf(LlmCallException.class);
    }

    @Test
    @DisplayName("熔断器关闭时 invokeLlm 正常执行并记录成功")
    void should_recordSuccess_whenCircuitClosed() {
        CircuitBreaker cb = mock(CircuitBreaker.class);
        when(cb.tryAcquirePermission()).thenReturn(true);
        ChatClient.Builder b = mock(ChatClient.Builder.class);
        when(b.build()).thenReturn(mock(ChatClient.class));
        ChatService service = new ChatService(mock(ChatClient.class), b,
                mock(Observability.class),
                providerOf((LlmProviderRegistry) null),
                providerOf(cb), 0);

        assertThat(service.invokeLlm(() -> "circuit-ok")).isEqualTo("circuit-ok");
    }

    @Test
    @DisplayName("熔断器关闭但调用失败时通知 CB.onError")
    void should_notifyCircuitBreakerOnError_when_callFails() {
        CircuitBreaker cb = mock(CircuitBreaker.class);
        when(cb.tryAcquirePermission()).thenReturn(true);
        ChatClient.Builder b = mock(ChatClient.Builder.class);
        when(b.build()).thenReturn(mock(ChatClient.class));
        ChatService service = new ChatService(mock(ChatClient.class), b,
                mock(Observability.class),
                providerOf((LlmProviderRegistry) null),
                providerOf(cb), 0);

        assertThatThrownBy(() -> service.invokeLlm(() -> {
            throw new RuntimeException("fail");
        })).isInstanceOf(LlmCallException.class);
    }
}
