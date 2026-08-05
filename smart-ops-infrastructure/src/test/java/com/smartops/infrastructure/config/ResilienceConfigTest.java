package com.smartops.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ResilienceConfig} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class ResilienceConfigTest {

    @Test
    @DisplayName("llmCircuitBreaker 返回已配置的熔断器")
    void should_createCircuitBreaker_when_defaultConfig() {
        ResilienceConfig config = new ResilienceConfig();
        ReflectionTestUtils.setField(config, "failureRateThreshold", 50);
        ReflectionTestUtils.setField(config, "slowCallRateThreshold", 80);
        ReflectionTestUtils.setField(config, "slowCallDurationThresholdMs", 5000L);
        ReflectionTestUtils.setField(config, "waitDurationOpenSeconds", 30);
        ReflectionTestUtils.setField(config, "permittedCallsHalfOpen", 3);
        ReflectionTestUtils.setField(config, "slidingWindowSizeSeconds", 60);

        CircuitBreaker cb = config.llmCircuitBreaker();
        assertThat(cb).isNotNull();
        assertThat(cb.getName()).isEqualTo("llm");
        assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50);
    }

    @Test
    @DisplayName("自定义阈值正确透传")
    void should_respectCustomThresholds() {
        ResilienceConfig config = new ResilienceConfig();
        ReflectionTestUtils.setField(config, "failureRateThreshold", 30);
        ReflectionTestUtils.setField(config, "slowCallRateThreshold", 60);
        ReflectionTestUtils.setField(config, "slowCallDurationThresholdMs", 3000L);
        ReflectionTestUtils.setField(config, "waitDurationOpenSeconds", 60);
        ReflectionTestUtils.setField(config, "permittedCallsHalfOpen", 5);
        ReflectionTestUtils.setField(config, "slidingWindowSizeSeconds", 120);

        CircuitBreaker cb = config.llmCircuitBreaker();
        assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(30);
    }
}
