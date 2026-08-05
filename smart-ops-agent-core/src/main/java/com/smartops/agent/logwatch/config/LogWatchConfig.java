package com.smartops.agent.logwatch.config;

import com.smartops.agent.logwatch.AlertPipelineService;
import com.smartops.agent.logwatch.anomaly.StatisticalBaselineDetector;
import com.smartops.agent.logwatch.LogKeywordMatcher;
import com.smartops.agent.logwatch.pipeline.AnalysisLayer;
import com.smartops.agent.logwatch.pipeline.impl.L0SuppressionLayer;
import com.smartops.agent.logwatch.pipeline.impl.L1ClassifyLayer;
import com.smartops.agent.logwatch.pipeline.impl.L2RagLayer;
import com.smartops.agent.logwatch.pipeline.impl.L3LlmLayer;
import com.smartops.agent.logwatch.pipeline.impl.L4SupervisorLayer;
import com.smartops.agent.logwatch.pipeline.impl.MlClassifyLayer;
import com.smartops.agent.orchestrator.SupervisorAgent;
import com.smartops.common.exception.LogWatchException;
import com.smartops.domain.knowledge.KnowledgeRetriever;
import com.smartops.domain.knowledge.port.KnowledgeRepository;
import com.smartops.domain.logwatch.port.AlertRepository;
import com.smartops.domain.logwatch.port.LogLevelClassifier;
import com.smartops.infrastructure.chat.ChatService;
import com.smartops.infrastructure.logwatch.LogSource;
import com.smartops.infrastructure.logwatch.impl.FileTailLogSource;
import com.smartops.infrastructure.logwatch.impl.JarProcessLogSource;
import com.smartops.infrastructure.logwatch.impl.JpsProcessLocator;
import com.smartops.infrastructure.logwatch.impl.SseAlertNotifier;
import com.smartops.infrastructure.logwatch.ml.TribuoLogLevelClassifier;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 日志采集分析装配（阶段五 logwatch 全链路）。
 *
 * <p>类级 {@code @ConditionalOnProperty("smartops.logwatch.enabled")} 总开关，
 * 默认关闭：无采集源与分析管线 Bean，应用照常启动。开启后装配
 * 关键字预过滤 → L0 抑制 → L1 正则定级 → L2 ML 定级 → L3 RAG → L4 LLM → L5 会诊
 * 六层管线，并按 {@code sources} 创建文件/jar 采集源，由 {@link LogWatchRunner} 托管生命周期。
 * L2 ML 定级层默认关闭（{@code smartops.logwatch.ml.enabled}），开启后
 * L1 未命中事件改为放行待定，由 ML 层裁决（阶段十五，ADR-019）。</p>
 *
 * <p>SseAlertNotifier 为基础设施组件常驻（不随开关条件化），logwatch 关闭时
 * SSE 端点仅剩心跳，保证 API 层注入不因开关缺失而启动失败。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "smartops.logwatch.enabled", havingValue = "true")
@EnableConfigurationProperties(LogWatchProperties.class)
public class LogWatchConfig {

    /** jar 进程日志定位失败重试周期。 */
    private static final Duration LOCATE_RETRY_INTERVAL = Duration.ofSeconds(30);
    @Bean
    public StatisticalBaselineDetector anomalyDetector() {
        return new StatisticalBaselineDetector(0.5);
    }

    /**
     * 关键字预过滤器：合并所有采集源的自定义关键字。
     *
     * @param properties 配置属性
     * @return 关键字匹配器
     */
    @Bean
    public LogKeywordMatcher logKeywordMatcher(LogWatchProperties properties) {
        List<String> keywords = properties.getSources().stream()
                .flatMap(source -> source.getKeywords().stream())
                .distinct()
                .toList();
        return new LogKeywordMatcher(keywords, properties.getExcludeKeywords());
    }

    /**
     * L0 告警抑制层（同指纹时间窗合并）。
     *
     * @param properties 配置属性
     * @return L0 分析层
     */
    @Bean
    public L0SuppressionLayer l0SuppressionLayer(LogWatchProperties properties) {
        return new L0SuppressionLayer(
                Duration.ofSeconds(properties.getL0().getWindowSeconds()), Clock.systemUTC());
    }

    /**
     * L1 正则定级层：ML 层开启时切 defer 模式（未命中放行待定，由 ML 裁决）。
     *
     * @param properties 配置属性
     * @return L1 分析层
     */
    @Bean
    public L1ClassifyLayer l1ClassifyLayer(LogWatchProperties properties) {
        return new L1ClassifyLayer(properties.getMl().isEnabled());
    }

    /**
     * ML 级别分类器（阶段十五）：构造即完成训练与留出集评估，
     * 准确率不达标时 {@code isReady()=false}，ML 层自动回退旧行为。
     * 外部训练数据路径优先，缺省用 classpath 内置种子数据。
     *
     * @param properties   配置属性
     * @param seedResource 内置种子训练数据资源
     * @return 级别分类器
     */
    @Bean
    @ConditionalOnProperty(name = "smartops.logwatch.ml.enabled", havingValue = "true")
    public LogLevelClassifier logLevelClassifier(LogWatchProperties properties,
                                                 @Value("classpath:ml/log-level-seed.tsv")
                                                 Resource seedResource) {
        LogWatchProperties.Ml ml = properties.getMl();
        Resource trainingData = ml.getTrainingDataPath() != null && !ml.getTrainingDataPath().isBlank()
                ? new FileSystemResource(ml.getTrainingDataPath())
                : seedResource;
        return new TribuoLogLevelClassifier(trainingData, ml.getMinAccuracy(), ml.getSeed());
    }

    /**
     * L2 ML 定级层：分类器 Bean 不存在（ML 未开启）时层内恒抑制待定事件，
     * 与传统模式 L1 的 INFO+SUPPRESS 行为一致，装配无需条件化。
     *
     * @param classifierProvider 级别分类器提供者
     * @param properties         配置属性
     * @return L2 ML 分析层
     */
    @Bean
    public MlClassifyLayer mlClassifyLayer(ObjectProvider<LogLevelClassifier> classifierProvider,
                                           LogWatchProperties properties) {
        return new MlClassifyLayer(classifierProvider.getIfAvailable(),
                properties.getMl().getConfidenceThreshold());
    }

    /**
     * L3 知识库 RAG 层：无 KnowledgeRetriever Bean（ES 未开启）时自动降级跳过。
     *
     * @param retrieverProvider 知识检索端口提供者
     * @return L3 分析层
     */
    @Bean
    public L2RagLayer l2RagLayer(ObjectProvider<KnowledgeRetriever> retrieverProvider) {
        return new L2RagLayer(retrieverProvider.getIfAvailable());
    }

    /**
     * L3 LLM 根因分析层：系统提示词外置 {@code prompts/logwatch-root-cause.st}。
     *
     * @param chatService  LLM 唯一入口
     * @param properties   配置属性
     * @param promptResource 系统提示词资源
     * @return L3 分析层
     * @throws IOException 提示词读取失败时
     */
    @Bean
    public L3LlmLayer l3LlmLayer(ChatService chatService, LogWatchProperties properties,
                                 @Value("classpath:prompts/logwatch-root-cause.st")
                                 Resource promptResource) throws IOException {
        String systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        return new L3LlmLayer(chatService, systemPrompt,
                properties.getL3().getRatePerMinute(),
                properties.getL3().getEscalateOccurrenceThreshold(),
                Clock.systemUTC());
    }

    /**
     * L4 Supervisor 会诊层（日上限保护）。
     *
     * @param supervisorAgent Supervisor 编排器
     * @param properties      配置属性
     * @return L4 分析层
     */
    @Bean
    public L4SupervisorLayer l4SupervisorLayer(SupervisorAgent supervisorAgent,
                                               LogWatchProperties properties) {
        return new L4SupervisorLayer(supervisorAgent,
                properties.getL4().getDailyLimit(), Clock.systemUTC());
    }

    /**
     * 分析管线服务（装配全部分析层，按 order 升序执行）。
     *
     * @param matcher    关键字预过滤器
     * @param layers     全部分析层 Bean
     * @param repository 告警持久化端口
     * @param notifier   告警实时通知器
     * @param properties 配置属性
     * @return 分析管线服务
     */
    @Bean
    public AlertPipelineService alertPipelineService(LogKeywordMatcher matcher,
                                                     List<AnalysisLayer> layers,
                                                     AlertRepository repository,
                                                     SseAlertNotifier notifier,
                                                     LogWatchProperties properties,
                                                     ObjectProvider<KnowledgeRepository> knowledgeProvider,
                                                     StatisticalBaselineDetector anomalyDetector,
                                                     org.springframework.context.ApplicationEventPublisher eventPublisher) {
        return new AlertPipelineService(matcher, layers, repository, notifier,
                knowledgeProvider.getIfAvailable(),
                anomalyDetector,
                properties.getQueueCapacity(),
                eventPublisher,
                properties.getMl().isEnabled());
    }

    /**
     * 分析队列指标绑定器：注册丢弃计数 Gauge
     * （指标名 {@code smartops.logwatch.queue.dropped}），
     * 由 Actuator 自动绑定并随 /actuator/prometheus 导出。
     *
     * @param pipeline 分析管线服务
     * @return 指标绑定器
     */
    @Bean
    public MeterBinder logwatchQueueMetrics(AlertPipelineService pipeline) {
        return registry -> Gauge.builder("smartops.logwatch.queue.dropped", pipeline,
                AlertPipelineService::getDroppedCount).register(registry);
    }

    /**
     * ML 定级救援计数指标绑定器（指标名 {@code smartops.logwatch.ml.rescued}）：
     * 正则漏判、被 ML 层高置信定级放行的事件总数。
     *
     * @param mlLayer L2 ML 分析层
     * @return 指标绑定器
     */
    @Bean
    public MeterBinder logwatchMlMetrics(MlClassifyLayer mlLayer) {
        return registry -> Gauge.builder("smartops.logwatch.ml.rescued", mlLayer,
                MlClassifyLayer::getRescuedCount).register(registry);
    }

    /**
     * 采集源与管线生命周期托管：就绪后启动，关闭时停止。
     *
     * @param properties 配置属性
     * @param pipeline   分析管线服务
     * @return 生命周期托管器
     */
    @Bean
    public LogWatchRunner logWatchRunner(LogWatchProperties properties,
                                         AlertPipelineService pipeline) {
        Duration pollInterval = Duration.ofMillis(properties.getPollIntervalMs());
        Path stateDir = Path.of(properties.getStateDir());
        List<LogSource> sources = new ArrayList<>();
        for (LogWatchProperties.Source source : properties.getSources()) {
            sources.add(createSource(source, stateDir, pollInterval, pipeline));
        }
        return new LogWatchRunner(sources, pipeline);
    }

    /**
     * 按类型创建单个采集源。
     */
    private LogSource createSource(LogWatchProperties.Source source, Path stateDir,
                                   Duration pollInterval, AlertPipelineService pipeline) {
        String type = source.getType() == null ? "" : source.getType().toLowerCase();
        return switch (type) {
            case "file" -> new FileTailLogSource(
                    Path.of(source.getPath()), stateDir, pollInterval, pipeline::onEvent);
            case "jar" -> new JarProcessLogSource(
                    source.getPath(), new JpsProcessLocator(), stateDir,
                    pollInterval, LOCATE_RETRY_INTERVAL, pipeline::onEvent);
            default -> throw new LogWatchException("未知采集源类型: " + source.getType());
        };
    }
}
