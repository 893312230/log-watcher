package com.smartops.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return instance; }
            @Override public T getIfAvailable() { return instance; }
        };
    }

    private final HealthController controller = new HealthController(
            providerOf(null), providerOf(null));

    @Test
    @DisplayName("健康检查返回 UP 状态")
    void should_returnUpStatus() {
        Map<String, Object> result = controller.health();
        assertThat(result.get("status")).isEqualTo("UP");
    }

    @Test
    @DisplayName("健康检查返回应用名称")
    void should_returnAppName() {
        assertThat(controller.health().get("application")).isEqualTo("smart-ops-agent");
    }

    @Test
    @DisplayName("DB/Redis 为 null 时返回 UP（跳过检测）")
    void should_returnUp_when_noDbOrRedis() {
        assertThat(controller.health().get("db")).isEqualTo("UP");
    }
}
