package com.smartops.agent.logwatch;

import com.smartops.agent.logwatch.anomaly.AnomalyDetector;
import com.smartops.agent.logwatch.pipeline.AnalysisLayer;
import com.smartops.common.enums.AlertLevel;
import com.smartops.common.enums.AlertStatus;
import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.AnalysisOutcome;
import com.smartops.domain.logwatch.LogEvent;
import com.smartops.domain.logwatch.port.AlertNotifier;
import com.smartops.domain.logwatch.port.AlertRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertPipelineService} 单元测试。
 *
 * <p>覆盖：关键字预过滤、层按序执行、SUPPRESS 终止、落库+通知、
 * 队列背压计数、层异常容错、启停幂等。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AlertPipelineServiceTest {

    private AlertRepository repository;
    private AlertNotifier notifier;
    private CountDownLatch savedLatch;
    private CountDownLatch notifiedLatch;
    private AlertPipelineService service;

    @BeforeEach
    void setUp() {
        repository = mock(AlertRepository.class);
        notifier = mock(AlertNotifier.class);
        savedLatch = new CountDownLatch(1);
        notifiedLatch = new CountDownLatch(1);
        when(repository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert saved = inv.getArgument(0, Alert.class).withId(1L);
            savedLatch.countDown();
            return saved;
        });
        doAnswer(inv -> {
            notifiedLatch.countDown();
            return null;
        }).when(notifier).publish(any(Alert.class));
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.stop();
        }
    }

    private AlertPipelineService newService(List<AnalysisLayer> layers) {
        return new AlertPipelineService(
                new LogKeywordMatcher(List.of("余额不足")), layers,
                repository, notifier, null, null, 2);
    }

    private LogEvent event(String content) {
        return new LogEvent("app.log", content, Instant.now());
    }

    @Test
    @DisplayName("关键字命中的事件经各层分析后落库并通知")
    void should_persistAndNotify_when_keywordMatched() throws Exception {
        AnalysisLayer l1 = new AnalysisLayer() {
            @Override
            public int order() {
                return 1;
            }

            @Override
            public AnalysisOutcome apply(com.smartops.domain.logwatch.AnalysisContext ctx) {
                ctx.markLayerReached(1);
                ctx.setLevel(AlertLevel.ERROR);
                ctx.setAnalysis("分析结论");
                ctx.setSuggestion("解决建议");
                return AnalysisOutcome.complete();
            }
        };
        service = newService(List.of(l1));
        service.start();

        service.onEvent(event("2026-07-22 10:00:00 ERROR db timeout"));

        assertThat(savedLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(notifiedLatch.await(3, TimeUnit.SECONDS)).isTrue();
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(notifier).publish(captor.capture());
        Alert alert = captor.getValue();
        assertThat(alert.id()).isEqualTo(1L);
        assertThat(alert.level()).isEqualTo(AlertLevel.ERROR);
        assertThat(alert.message()).isEqualTo("2026-07-22 10:00:00 ERROR db timeout");
        assertThat(alert.analysis()).isEqualTo("分析结论");
        assertThat(alert.status()).isEqualTo(AlertStatus.OPEN);
        assertThat(alert.layerReached()).isEqualTo(1);
    }

    @Test
    @DisplayName("关键字未命中的事件不进入管线")
    void should_skipEvent_when_keywordNotMatched() {
        service = newService(List.of());
        service.start();

        service.onEvent(event("2026-07-22 10:00:00 INFO service started"));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("ML 直通模式：关键字未命中的事件也进入管线落库（级别缺失按 ERROR 兜底）")
    void should_passThrough_when_mlPassthroughAndKeywordNotMatched() throws Exception {
        service = new AlertPipelineService(
                new LogKeywordMatcher(List.of("余额不足")), List.of(),
                repository, notifier, null, null, 2, null, true);
        service.start();

        service.onEvent(event("Connection refused by db-01:3306"));

        assertThat(savedLatch.await(3, TimeUnit.SECONDS)).isTrue();
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(notifier).publish(captor.capture());
        assertThat(captor.getValue().level()).isEqualTo(AlertLevel.ERROR);
    }

    @Test
    @DisplayName("ML 直通模式：命中排除子串的事件仍被过滤")
    void should_stillFilterExcluded_when_mlPassthrough() {
        service = new AlertPipelineService(
                new LogKeywordMatcher(List.of(), List.of("c.s.agent.logwatch")), List.of(),
                repository, notifier, null, null, 2, null, true);
        service.start();

        service.onEvent(event("c.s.agent.logwatch.MlClassifyLayer : ML 定级救援"));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("传统模式：异常检测评分低于阈值时跳过分析")
    void should_skipAnalysis_when_anomalyScoreBelowThreshold() {
        AnomalyDetector detector = mock(AnomalyDetector.class);
        when(detector.score(any())).thenReturn(0.0);
        when(detector.threshold()).thenReturn(0.5);
        service = new AlertPipelineService(
                new LogKeywordMatcher(List.of()), List.of(),
                repository, notifier, null, detector, 2);
        service.start();

        service.onEvent(event("2026-07-22 10:00:00 ERROR boom"));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("ML 直通模式：异常检测低分不拦截（基线语义不适用于全量流）")
    void should_bypassAnomalyGate_when_mlPassthrough() throws Exception {
        AnomalyDetector detector = mock(AnomalyDetector.class);
        when(detector.score(any())).thenReturn(0.0);
        when(detector.threshold()).thenReturn(0.5);
        service = new AlertPipelineService(
                new LogKeywordMatcher(List.of()), List.of(),
                repository, notifier, null, detector, 2, null, true);
        service.start();

        service.onEvent(event("Connection refused by db-01:3306"));

        assertThat(savedLatch.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("层产出 SUPPRESS 时终止且不落库")
    void should_abortPipeline_when_layerSuppresses() {
        AnalysisLayer suppressor = new AnalysisLayer() {
            @Override
            public int order() {
                return 0;
            }

            @Override
            public AnalysisOutcome apply(com.smartops.domain.logwatch.AnalysisContext ctx) {
                return AnalysisOutcome.suppress();
            }
        };
        service = newService(List.of(suppressor));
        service.start();

        service.onEvent(event("ERROR duplicated within window"));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("层按 order 升序执行")
    void should_executeLayersInOrder_when_multipleLayers() throws Exception {
        StringBuilder sequence = new StringBuilder();
        AnalysisLayer later = new AnalysisLayer() {
            @Override
            public int order() {
                return 5;
            }

            @Override
            public AnalysisOutcome apply(com.smartops.domain.logwatch.AnalysisContext ctx) {
                sequence.append("B");
                return AnalysisOutcome.complete();
            }
        };
        AnalysisLayer earlier = new AnalysisLayer() {
            @Override
            public int order() {
                return 1;
            }

            @Override
            public AnalysisOutcome apply(com.smartops.domain.logwatch.AnalysisContext ctx) {
                sequence.append("A");
                return AnalysisOutcome.proceed();
            }
        };
        // 乱序传入，验证装配时按 order 排序
        service = newService(List.of(later, earlier));
        service.start();

        service.onEvent(event("ERROR order check"));

        assertThat(savedLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(sequence.toString()).isEqualTo("AB");
    }

    @Test
    @DisplayName("层抛异常时容错继续后续层，告警正常落库")
    void should_tolerateLayerFailure_when_layerThrows() throws Exception {
        AnalysisLayer broken = new AnalysisLayer() {
            @Override
            public int order() {
                return 1;
            }

            @Override
            public AnalysisOutcome apply(com.smartops.domain.logwatch.AnalysisContext ctx) {
                throw new IllegalStateException("layer exploded");
            }
        };
        AnalysisLayer finisher = new AnalysisLayer() {
            @Override
            public int order() {
                return 2;
            }

            @Override
            public AnalysisOutcome apply(com.smartops.domain.logwatch.AnalysisContext ctx) {
                ctx.setLevel(AlertLevel.ERROR);
                return AnalysisOutcome.complete();
            }
        };
        service = newService(List.of(broken, finisher));
        service.start();

        service.onEvent(event("ERROR fault tolerance"));

        assertThat(savedLatch.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("队列满时新事件丢弃并计数，不阻塞采集线程")
    void should_dropAndCount_when_queueFull() {
        // 不启动消费线程，队列必然积压：容量 2 容纳 2 条，其余 8 条全部丢弃
        service = newService(List.of());

        for (int i = 0; i < 10; i++) {
            service.onEvent(event("ERROR flood-" + i));
        }

        assertThat(service.getDroppedCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("领域事件发布器异常且告警无 id 时主流程不受影响")
    void should_toleratePublisherFailure_when_eventBusDown() throws Exception {
        org.springframework.context.ApplicationEventPublisher publisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        org.mockito.Mockito.doThrow(new RuntimeException("bus down"))
                .when(publisher).publishEvent(any());
        when(repository.save(any(Alert.class))).thenAnswer(inv -> {
            savedLatch.countDown();
            return inv.getArgument(0, Alert.class); // 不分配 id
        });
        service = new AlertPipelineService(
                new LogKeywordMatcher(List.of("余额不足")), List.of(),
                repository, notifier, null, null, 2, publisher);
        service.start();

        service.onEvent(event("2026-07-22 10:00:00 ERROR 余额不足"));

        assertThat(savedLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(notifiedLatch.await(3, TimeUnit.SECONDS)).isTrue();
        verify(publisher, org.mockito.Mockito.timeout(3000))
                .publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    @DisplayName("L4+ 分析完成后自动写入知识库（来源 LOGWATCH）")
    void should_saveKnowledgeEntry_when_layerReachedL4() throws Exception {
        com.smartops.domain.knowledge.port.KnowledgeRepository knowledgeRepository =
                mock(com.smartops.domain.knowledge.port.KnowledgeRepository.class);
        CountDownLatch knowledgeLatch = new CountDownLatch(1);
        doAnswer(inv -> {
            knowledgeLatch.countDown();
            return inv.getArgument(0);
        }).when(knowledgeRepository).save(any(com.smartops.domain.knowledge.KnowledgeEntry.class));
        AnalysisLayer l3 = new AnalysisLayer() {
            @Override
            public int order() {
                return 4;
            }

            @Override
            public AnalysisOutcome apply(com.smartops.domain.logwatch.AnalysisContext ctx) {
                ctx.markLayerReached(4);
                ctx.setLevel(AlertLevel.ERROR);
                ctx.setAnalysis("根因分析");
                ctx.setSuggestion("扩容");
                return AnalysisOutcome.complete();
            }
        };
        service = new AlertPipelineService(
                new LogKeywordMatcher(List.of("余额不足")), List.of(l3),
                repository, notifier, knowledgeRepository, null, 2);
        service.start();

        service.onEvent(event("2026-07-22 10:00:00 ERROR 余额不足"));

        assertThat(knowledgeLatch.await(3, TimeUnit.SECONDS)).isTrue();
        ArgumentCaptor<com.smartops.domain.knowledge.KnowledgeEntry> captor =
                ArgumentCaptor.forClass(com.smartops.domain.knowledge.KnowledgeEntry.class);
        verify(knowledgeRepository).save(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo("LOGWATCH");
        assertThat(captor.getValue().rootCause()).isEqualTo("根因分析");
    }

    @Test
    @DisplayName("知识库自动入库失败不影响告警主流程")
    void should_tolerateKnowledgeSaveFailure_when_repositoryDown() throws Exception {
        com.smartops.domain.knowledge.port.KnowledgeRepository knowledgeRepository =
                mock(com.smartops.domain.knowledge.port.KnowledgeRepository.class);
        doAnswer(inv -> {
            throw new RuntimeException("ES down");
        }).when(knowledgeRepository).save(any(com.smartops.domain.knowledge.KnowledgeEntry.class));
        AnalysisLayer l3 = new AnalysisLayer() {
            @Override
            public int order() {
                return 4;
            }

            @Override
            public AnalysisOutcome apply(com.smartops.domain.logwatch.AnalysisContext ctx) {
                ctx.markLayerReached(4);
                ctx.setLevel(AlertLevel.ERROR);
                return AnalysisOutcome.complete();
            }
        };
        service = new AlertPipelineService(
                new LogKeywordMatcher(List.of("余额不足")), List.of(l3),
                repository, notifier, knowledgeRepository, null, 2);
        service.start();

        service.onEvent(event("2026-07-22 10:00:00 ERROR 余额不足"));

        assertThat(savedLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(notifiedLatch.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("告警有 id 时事件载荷携带真实 alertId")
    void should_publishWithRealAlertId_when_idAssigned() throws Exception {
        org.springframework.context.ApplicationEventPublisher publisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        service = new AlertPipelineService(
                new LogKeywordMatcher(List.of("余额不足")), List.of(),
                repository, notifier, null, null, 2, publisher);
        service.start();

        service.onEvent(event("2026-07-22 10:00:00 ERROR 余额不足"));

        ArgumentCaptor<com.smartops.domain.event.OpsEvent> captor =
                ArgumentCaptor.forClass(com.smartops.domain.event.OpsEvent.class);
        verify(publisher, org.mockito.Mockito.timeout(3000))
                .publishEvent(captor.capture());
        assertThat(captor.getValue().payload()).containsEntry("alertId", 1L);
    }

    @Test
    @DisplayName("start/stop 幂等")
    void should_beIdempotent_when_startStopRepeated() {
        service = newService(List.of());

        service.start();
        service.start();
        service.stop();
        service.stop();
    }
}
