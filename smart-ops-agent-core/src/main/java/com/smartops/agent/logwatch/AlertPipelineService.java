package com.smartops.agent.logwatch;

import com.smartops.agent.logwatch.anomaly.AnomalyDetector;
import com.smartops.agent.logwatch.pipeline.AnalysisLayer;
import com.smartops.domain.event.OpsEvent;
import com.smartops.domain.knowledge.KnowledgeEntry;
import com.smartops.domain.knowledge.port.KnowledgeRepository;
import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import com.smartops.domain.logwatch.LogEvent;
import com.smartops.domain.logwatch.port.AlertNotifier;
import com.smartops.domain.logwatch.port.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 告警分析管线服务。
 *
 * <p>日志监控的核心编排：采集源事件 → 关键字预过滤 → 有界队列 →
 * 单分析线程驱动 L0→L5 分层管线（L0 抑制 → L1 正则定级 → L2 ML 定级 →
 * L3 知识库 → L4 LLM → L5 会诊）→ 落库 → 实时通知。
 * 线程模型刻意保守（适配 2C/1.8G 小内存服务器）：</p>
 * <ul>
 *   <li>采集线程只做预过滤与入队（{@link #onEvent} 永不阻塞，溢出丢弃计数）</li>
 *   <li>单分析线程串行消费，LLM 调用天然串行，无需额外并发控制</li>
 *   <li>层异常逐层容错，单层失败不拖垮整条管线</li>
 * </ul>
 *
 * <p>生命周期：{@link #start()} 启动消费、{@link #stop()} 停止，均幂等。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class AlertPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AlertPipelineService.class);

    /** 消费空转时的轮询间隔（毫秒）。 */
    private static final long POLL_TIMEOUT_MS = 200L;

    private final LogKeywordMatcher matcher;
    private final List<AnalysisLayer> layers;
    private final AlertRepository repository;
    private final AlertNotifier notifier;
    private final KnowledgeRepository knowledgeRepository;
    private final AnomalyDetector anomalyDetector;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean mlPassthrough;
    private final BlockingQueue<LogEvent> queue;
    private final ExecutorService consumer;
    private final AtomicLong droppedCount = new AtomicLong();

    private volatile boolean running;

    /**
     * 构造告警分析管线服务。
     *
     * @param matcher             关键字预过滤器
     * @param layers              分析层列表（装配时按 order 升序排序）
     * @param repository          告警持久化端口
     * @param notifier            告警实时通知端口
     * @param knowledgeRepository 知识库持久化端口（可为 null）
     * @param anomalyDetector     异常检测器（可为 null）
     * @param queueCapacity       事件队列容量（背压边界）
     */
    public AlertPipelineService(LogKeywordMatcher matcher, List<AnalysisLayer> layers,
                                AlertRepository repository, AlertNotifier notifier,
                                KnowledgeRepository knowledgeRepository,
                                AnomalyDetector anomalyDetector,
                                int queueCapacity) {
        this(matcher, layers, repository, notifier, knowledgeRepository,
                anomalyDetector, queueCapacity, null);
    }

    /**
     * 构造告警分析管线服务（含领域事件发布器，用于 Webhook 订阅投递）。
     *
     * @param matcher             关键字预过滤器
     * @param layers              分析层列表（装配时按 order 升序排序）
     * @param repository          告警持久化端口
     * @param notifier            告警实时通知端口
     * @param knowledgeRepository 知识库持久化端口（可为 null）
     * @param anomalyDetector     异常检测器（可为 null）
     * @param queueCapacity       事件队列容量（背压边界）
     * @param eventPublisher      领域事件发布器（可为 null，null 时不发布事件）
     */
    public AlertPipelineService(LogKeywordMatcher matcher, List<AnalysisLayer> layers,
                                AlertRepository repository, AlertNotifier notifier,
                                KnowledgeRepository knowledgeRepository,
                                AnomalyDetector anomalyDetector,
                                int queueCapacity,
                                ApplicationEventPublisher eventPublisher) {
        this(matcher, layers, repository, notifier, knowledgeRepository,
                anomalyDetector, queueCapacity, eventPublisher, false);
    }

    /**
     * 构造告警分析管线服务（含 ML 预过滤直通开关）。
     *
     * @param matcher             关键字预过滤器
     * @param layers              分析层列表（装配时按 order 升序排序）
     * @param repository          告警持久化端口
     * @param notifier            告警实时通知端口
     * @param knowledgeRepository 知识库持久化端口（可为 null）
     * @param anomalyDetector     异常检测器（可为 null）
     * @param queueCapacity       事件队列容量（背压边界）
     * @param eventPublisher      领域事件发布器（可为 null，null 时不发布事件）
     * @param mlPassthrough       true（ML 定级开启）时预过滤不要求关键字命中、
     *                            仅判定排除子串——否则 ML 层收不到它要救援的
     *                            正则词表外事件（ADR-019）
     */
    public AlertPipelineService(LogKeywordMatcher matcher, List<AnalysisLayer> layers,
                                AlertRepository repository, AlertNotifier notifier,
                                KnowledgeRepository knowledgeRepository,
                                AnomalyDetector anomalyDetector,
                                int queueCapacity,
                                ApplicationEventPublisher eventPublisher,
                                boolean mlPassthrough) {
        this.matcher = matcher;
        this.mlPassthrough = mlPassthrough;
        this.layers = layers.stream()
                .sorted(Comparator.comparingInt(AnalysisLayer::order))
                .toList();
        this.repository = repository;
        this.notifier = notifier;
        this.knowledgeRepository = knowledgeRepository;
        this.anomalyDetector = anomalyDetector;
        this.eventPublisher = eventPublisher;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.consumer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "logwatch-analysis");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动分析消费线程。
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        consumer.submit(this::consumeLoop);
        log.info("告警分析管线启动，分析层数={}", layers.size());
    }

    /**
     * 停止分析消费线程。
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        consumer.shutdownNow();
        log.info("告警分析管线停止");
    }

    /**
     * 采集源事件入口（由基础设施层以方法引用绑定为 LogEventListener）。
     *
     * <p>关键字预过滤 + 非阻塞入队；队列满时丢弃并计数，
     * 保证采集线程永不被分析速度拖住。ML 直通模式
     * （{@code mlPassthrough=true}）下预过滤仅判定排除子串，
     * 关键字是否命中交由 L1 正则与 L2 ML 定级层裁决。</p>
     *
     * @param event 日志事件
     */
    public void onEvent(LogEvent event) {
        if (mlPassthrough) {
            if (matcher.isExcluded(event.content())) {
                return;
            }
        } else if (matcher.match(event.content()).isEmpty()) {
            return;
        }
        if (!queue.offer(event)) {
            long dropped = droppedCount.incrementAndGet();
            if (dropped % 100 == 1) {
                log.warn("分析队列已满，事件丢弃（累计 {} 条）", dropped);
            }
        }
    }

    /**
     * 获取因队列满而丢弃的事件总数（监控指标用）。
     *
     * @return 累计丢弃数
     */
    public long getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * 消费主循环：取事件 → 分层分析 → 落库通知。
     */
    private void consumeLoop() {
        while (running || !queue.isEmpty()) {
            try {
                LogEvent event = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (event != null) {
                    process(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("告警处理异常: {}", e.toString());
            }
        }
    }

    /**
     * 单事件处理：分层管线 + 落库 + 通知。
     */
    private void process(LogEvent event) {
        // 阶段七：异常评分，非异常跳过分析节省资源。
        // ML 直通模式下不套用：检测器基线假设输入是经关键字预过滤的稀疏可疑流，
        // 直通的全量常规日志会把基线刷成短间隔均匀流，导致真实错误也被判"非异常"
        if (!mlPassthrough && anomalyDetector != null
                && anomalyDetector.score(event) < anomalyDetector.threshold()) {
            return;
        }
        AnalysisContext context = new AnalysisContext(event);
        matcher.match(event.content()).ifPresent(context::setMatchedKeyword);

        for (AnalysisLayer layer : layers) {
            AnalysisOutcome outcome;
            try {
                outcome = layer.apply(context);
            } catch (RuntimeException e) {
                log.error("分析层执行异常 layer={}: {}",
                        layer.getClass().getSimpleName(), e.toString());
                continue;
            }
            if (outcome.verdict() == AnalysisOutcome.Verdict.SUPPRESS) {
                return;
            }
            if (outcome.verdict() == AnalysisOutcome.Verdict.COMPLETE) {
                break;
            }
        }
        persist(context);
    }

    /**
     * 上下文落库并实时通知。级别缺失（未经过 L1）时按 ERROR 兜底——
     * 事件既然通过了关键字预过滤，按错误对待最保守。
     */
    private void persist(AnalysisContext context) {
        Instant now = Instant.now();
        String content = context.getEvent().content();
        String firstLine = content.split("\n", 2)[0];

        Alert alert = Alert.create(
                        context.getFingerprint(),
                        context.getEvent().source(),
                        context.getLevel() == null
                                ? com.smartops.common.enums.AlertLevel.ERROR
                                : context.getLevel(),
                        context.getMatchedKeyword(),
                        firstLine,
                        content,
                        context.getLayerReached(),
                        now)
                .withOccurrence(context.getOccurrence(), now)
                .withAnalysis(context.getAnalysis(), context.getSuggestion(), now);

        Alert saved = repository.save(alert);
        try {
            notifier.publish(saved);
        } catch (RuntimeException e) {
            log.warn("告警通知失败（已落库不受影响）: {}", e.toString());
        }
        publishAlertCreated(saved);
        // L3+ 分析完成后自动写入知识库（来源=LOGWATCH）
        saveKnowledgeEntry(saved, context);
        log.info("告警落库 level={}, layer={}, source={}, fp={}",
                saved.level(), saved.layerReached(), saved.source(),
                saved.fingerprint().substring(0, 12));
    }

    /** 告警落库后发布 ALERT_CREATED 领域事件（发布器缺省或异常均不影响主流程）。 */
    private void publishAlertCreated(Alert saved) {
        if (eventPublisher == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(OpsEvent.of(OpsEvent.ALERT_CREATED, Map.of(
                    "alertId", saved.id() == null ? 0 : saved.id(),
                    "level", saved.level().name(),
                    "source", saved.source(),
                    "message", saved.message())));
        } catch (RuntimeException e) {
            log.warn("告警领域事件发布失败（不影响主流程）: {}", e.toString());
        }
    }

    private void saveKnowledgeEntry(Alert alert, AnalysisContext context) {
        // 阶段十五层号重编（L0-L5）：阈值 3→4，语义不变（到达 LLM 层才沉淀）
        if (knowledgeRepository == null || alert.layerReached() < 4) {
            return;
        }
        try {
            KnowledgeEntry entry = KnowledgeEntry.create(
                    alert.keyword() != null ? alert.keyword() + " — " + alert.message() : alert.message(),
                    alert.keyword(),
                    context.getAnalysis(),
                    context.getSuggestion(),
                    List.of(),
                    alert.level().name(),
                    List.of(),
                    "LOGWATCH",
                    alert.id(),
                    null, // serverConfigId 暂不关联，后续可通过日志路径匹配
                    "logwatch",
                    Instant.now());
            knowledgeRepository.save(entry);
            log.debug("知识库自动入库: title={}", entry.title());
        } catch (RuntimeException e) {
            log.warn("知识库自动入库失败（不影响告警主流程）: {}", e.toString());
        }
    }
}
