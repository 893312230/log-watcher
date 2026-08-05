package com.smartops.infrastructure.config;

import com.smartops.infrastructure.observability.AsyncAuditRecorder;
import com.smartops.infrastructure.persistence.audit.impl.AuditRepositoryImpl;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 可观测性装配（阶段五：审计记录器生命周期 + 审计队列指标）。
 *
 * <p>装配内容：
 * <ol>
 *   <li>{@link AsyncAuditRecorder} Bean——init/destroy 挂接 start/stop，
 *       随容器启停管理消费线程生命周期，停止时 drain 队列剩余事件</li>
 *   <li>审计队列 MeterBinder——注册队列深度与丢弃计数两个 Gauge
 *       （指标名 {@code smartops.audit.queue.size} /
 *       {@code smartops.audit.queue.dropped}），由 Actuator 自动绑定到
 *       应用 MeterRegistry，随 /actuator/prometheus 导出</li>
 * </ol></p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
public class ObservabilityConfig {

    /** 审计事件队列容量配置键（默认 2000，背压边界，满则丢弃计数）。 */
    @Value("${smartops.audit.queue-capacity:2000}")
    private int queueCapacity;

    /**
     * 异步审计记录器 Bean：容器启动后 start，关闭时 stop（drain 队列）。
     *
     * @param repository 审计持久化实现
     * @return 审计记录器
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public AsyncAuditRecorder auditRecorder(AuditRepositoryImpl repository) {
        return new AsyncAuditRecorder(repository, queueCapacity);
    }

    /**
     * 审计队列指标绑定器：队列深度 + 丢弃计数 Gauge。
     *
     * @param recorder 异步审计记录器
     * @return 指标绑定器
     */
    @Bean
    public MeterBinder auditQueueMetrics(AsyncAuditRecorder recorder) {
        return registry -> {
            Gauge.builder("smartops.audit.queue.dropped", recorder,
                    AsyncAuditRecorder::getDroppedCount).register(registry);
            Gauge.builder("smartops.audit.queue.size", recorder,
                    AsyncAuditRecorder::getQueueSize).register(registry);
        };
    }
}
