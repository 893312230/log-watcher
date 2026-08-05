package com.smartops.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j 配置（阶段五韧性增强）。
 *
 * <p>为 ChatService LLM 调用提供熔断保护：窗口内失败率/慢调用率超阈值
 * 时开启熔断，半开状态探测恢复后关闭。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
public class ResilienceConfig {

    @Value("${smartops.llm.circuit-breaker.failure-rate-threshold:50}")
    private int failureRateThreshold;

    @Value("${smartops.llm.circuit-breaker.slow-call-rate-threshold:80}")
    private int slowCallRateThreshold;

    @Value("${smartops.llm.circuit-breaker.slow-call-duration-threshold-ms:5000}")
    private long slowCallDurationThresholdMs;

    @Value("${smartops.llm.circuit-breaker.wait-duration-in-open-state-seconds:30}")
    private int waitDurationOpenSeconds;

    @Value("${smartops.llm.circuit-breaker.permitted-number-of-calls-in-half-open-state:3}")
    private int permittedCallsHalfOpen;

    @Value("${smartops.llm.circuit-breaker.sliding-window-size-seconds:60}")
    private int slidingWindowSizeSeconds;

    /** 熔断器名称。 */
    static final String LLM_CIRCUIT_BREAKER = "llm";

    /**
     * LLM 调用熔断器 Bean。
     *
     * @return 熔断器实例
     */
    @Bean
    public CircuitBreaker llmCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(Duration.ofMillis(slowCallDurationThresholdMs))
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationOpenSeconds))
                .permittedNumberOfCallsInHalfOpenState(permittedCallsHalfOpen)
                .slidingWindowSize(slidingWindowSizeSeconds)
                .build();
        return CircuitBreakerRegistry.of(config).circuitBreaker(LLM_CIRCUIT_BREAKER);
    }
}
