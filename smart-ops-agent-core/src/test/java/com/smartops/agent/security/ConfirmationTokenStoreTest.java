package com.smartops.agent.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ConfirmationTokenStore} 单元测试。
 *
 * <p>验证一次性确认令牌的生命周期：签发、验证消费、过期、
 * 绑定校验（会话/消息）、容量淘汰与一次性防重放。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ConfirmationTokenStoreTest {

    @Test
    @DisplayName("签发后可用匹配的会话与消息验证成功，且令牌被消费")
    void should_validateAndConsume_when_tokenMatches() {
        ConfirmationTokenStore store = new ConfirmationTokenStore();
        String token = store.issue("conv-1", "重启订单服务");

        assertThat(store.validateAndConsume(token, "conv-1", "重启订单服务")).isTrue();
        // 一次性：已被消费，再次验证失败
        assertThat(store.validateAndConsume(token, "conv-1", "重启订单服务")).isFalse();
        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("会话 ID 不匹配时验证失败")
    void should_reject_when_conversationMismatch() {
        ConfirmationTokenStore store = new ConfirmationTokenStore();
        String token = store.issue("conv-1", "重启订单服务");

        assertThat(store.validateAndConsume(token, "conv-2", "重启订单服务")).isFalse();
    }

    @Test
    @DisplayName("触发消息不匹配时验证失败")
    void should_reject_when_messageMismatch() {
        ConfirmationTokenStore store = new ConfirmationTokenStore();
        String token = store.issue("conv-1", "重启订单服务");

        assertThat(store.validateAndConsume(token, "conv-1", "删除数据库")).isFalse();
    }

    @Test
    @DisplayName("不存在的令牌验证失败")
    void should_reject_when_tokenNotExists() {
        ConfirmationTokenStore store = new ConfirmationTokenStore();

        assertThat(store.validateAndConsume("不存在的令牌", "conv-1", "重启订单服务")).isFalse();
    }

    @Test
    @DisplayName("null 或空白令牌验证失败")
    void should_reject_when_tokenNullOrBlank() {
        ConfirmationTokenStore store = new ConfirmationTokenStore();

        assertThat(store.validateAndConsume(null, "conv-1", "重启订单服务")).isFalse();
        assertThat(store.validateAndConsume("  ", "conv-1", "重启订单服务")).isFalse();
    }

    @Test
    @DisplayName("超过 TTL 的令牌验证失败并被清除")
    void should_reject_when_tokenExpired() throws InterruptedException {
        ConfirmationTokenStore store = new ConfirmationTokenStore(1);
        String token = store.issue("conv-1", "重启订单服务");
        Thread.sleep(10);

        assertThat(store.validateAndConsume(token, "conv-1", "重启订单服务")).isFalse();
        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("TTL 必须为正数")
    void should_throwIllegalArg_when_ttlNotPositive() {
        assertThatThrownBy(() -> new ConfirmationTokenStore(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfirmationTokenStore(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("签发时清理过期令牌")
    void should_purgeExpired_when_issuing() throws InterruptedException {
        ConfirmationTokenStore store = new ConfirmationTokenStore(1);
        store.issue("conv-1", "操作A");
        Thread.sleep(10);

        store.issue("conv-2", "操作B");

        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("不同会话的令牌互不影响")
    void should_isolateTokens_when_multipleConversations() {
        ConfirmationTokenStore store = new ConfirmationTokenStore();
        String tokenA = store.issue("conv-a", "重启A");
        String tokenB = store.issue("conv-b", "重启B");

        assertThat(store.validateAndConsume(tokenA, "conv-a", "重启A")).isTrue();
        assertThat(store.validateAndConsume(tokenB, "conv-b", "重启B")).isTrue();
    }
}
