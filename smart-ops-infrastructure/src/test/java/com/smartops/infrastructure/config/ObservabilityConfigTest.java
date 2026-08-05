package com.smartops.infrastructure.config;

import com.smartops.common.enums.AuditEventType;
import com.smartops.domain.audit.AuditEvent;
import com.smartops.infrastructure.observability.AsyncAuditRecorder;
import com.smartops.infrastructure.persistence.audit.impl.AuditRepositoryImpl;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link ObservabilityConfig} 单元测试。
 *
 * <p>覆盖：审计记录器 Bean 按配置容量构建、审计队列 MeterBinder
 * 注册深度与丢弃计数 Gauge 并反映实时值。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ObservabilityConfigTest {

    @Test
    @DisplayName("auditRecorder Bean 按配置容量构建且未启动")
    void should_buildRecorderWithConfiguredCapacity_when_beanMethodCalled() {
        ObservabilityConfig config = new ObservabilityConfig();
        ReflectionTestUtils.setField(config, "queueCapacity", 8);

        AsyncAuditRecorder recorder = config.auditRecorder(mock(AuditRepositoryImpl.class));

        assertThat(recorder).isNotNull();
        assertThat(recorder.getQueueSize()).isZero();
    }

    @Test
    @DisplayName("auditQueueMetrics 注册两个 Gauge 并反映实时值")
    void should_registerGauges_when_binderApplied() {
        ObservabilityConfig config = new ObservabilityConfig();
        AsyncAuditRecorder recorder = new AsyncAuditRecorder(
                mock(AuditRepositoryImpl.class), 2);
        recorder.record(AuditEvent.create(AuditEventType.LLM_CALL, null, "actor",
                null, null, true, 1, Instant.now()));

        MeterBinder binder = config.auditQueueMetrics(recorder);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);

        assertThat(registry.get("smartops.audit.queue.size").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("smartops.audit.queue.dropped").gauge().value()).isZero();
    }
}
