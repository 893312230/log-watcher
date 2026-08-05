package com.smartops.domain.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ServerConfig} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class ServerConfigTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    @Test
    @DisplayName("create 工厂方法构建带时间戳的配置")
    void should_createWithTimestamps() {
        ServerConfig c = ServerConfig.create("order-service", "192.168.1.1",
                "/opt/app.jar", "https://git/order", "/var/log/app.log",
                "订单服务", List.of("java", "spring"), NOW);
        assertThat(c.id()).isNull();
        assertThat(c.name()).isEqualTo("order-service");
        assertThat(c.host()).isEqualTo("192.168.1.1");
        assertThat(c.tags()).containsExactly("java", "spring");
        assertThat(c.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("名称为空时抛异常")
    void should_throw_when_nameBlank() {
        assertThatThrownBy(() -> ServerConfig.create("", null, null, null, null, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("withId 返回带 id 的副本")
    void should_returnCopyWithId() {
        ServerConfig c = ServerConfig.create("s1", null, null, null, null, null, null, NOW);
        ServerConfig withId = c.withId(1L);
        assertThat(withId.id()).isEqualTo(1L);
        assertThat(withId.name()).isEqualTo("s1");
    }

    @Test
    @DisplayName("tags 为 null 时返回空列表")
    void should_returnEmptyTags_when_null() {
        ServerConfig c = ServerConfig.create("s1", null, null, null, null, null, null, NOW);
        assertThat(c.tags()).isEmpty();
    }

    @Test
    @DisplayName("名称超过最大长度时抛异常")
    void should_throw_when_nameTooLong() {
        String longName = "x".repeat(ServerConfig.NAME_MAX_LENGTH + 1);
        assertThatThrownBy(() -> ServerConfig.create(longName, null, null, null, null, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
