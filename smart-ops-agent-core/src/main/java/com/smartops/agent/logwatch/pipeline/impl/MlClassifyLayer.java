package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.agent.logwatch.pipeline.AnalysisLayer;
import com.smartops.common.enums.AlertLevel;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import com.smartops.domain.logwatch.ClassificationResult;
import com.smartops.domain.logwatch.port.LogLevelClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * L2 机器学习定级层（阶段十五，ADR-019：正则漏判救援）。
 *
 * <p>位于 L1 正则之后、RAG 之前，只裁决 L1 defer 模式放行待定的
 * （{@code context.getLevel() == null}）事件；L1 已定级的事件直接放行、
 * 绝不改判——行为变化单调：相对现状只多出"被救援的告警"。</p>
 *
 * <p>降级即旧行为（与传统模式 L1 的 INFO+SUPPRESS 完全一致）：</p>
 * <ul>
 *   <li>分类器缺失 / 未就绪（准确率门禁不达标）→ SUPPRESS</li>
 *   <li>推理异常（违反端口弃权契约的实现）→ SUPPRESS</li>
 *   <li>置信度低于阈值 / 判为 INFO → SUPPRESS</li>
 * </ul>
 *
 * <p>仅当 ML 高置信判为 ERROR/WARN 时写入级别放行（救援），并计数
 * （{@link #getRescuedCount()}，经 Gauge {@code smartops.logwatch.ml.rescued} 导出）。</p>
 *
 * <p>线程约束：被分析线程单线程调用；计数器用 {@link AtomicLong} 保证指标线程读取安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class MlClassifyLayer implements AnalysisLayer {

    private static final Logger log = LoggerFactory.getLogger(MlClassifyLayer.class);

    private final LogLevelClassifier classifier;
    private final double confidenceThreshold;
    private final AtomicLong rescuedCount = new AtomicLong();

    /**
     * 构造 ML 定级层。
     *
     * @param classifier          级别分类器端口，可为 null（未开启 ML，本层恒抑制待定事件）
     * @param confidenceThreshold 采信预测的置信度下限
     */
    public MlClassifyLayer(LogLevelClassifier classifier, double confidenceThreshold) {
        this.classifier = classifier;
        this.confidenceThreshold = confidenceThreshold;
    }

    @Override
    public int order() {
        return 2;
    }

    @Override
    public AnalysisOutcome apply(AnalysisContext context) {
        if (context.getLevel() != null) {
            return AnalysisOutcome.proceed();
        }
        context.markLayerReached(2);
        if (classifier == null || !classifier.isReady()) {
            return AnalysisOutcome.suppress();
        }
        ClassificationResult result;
        try {
            result = classifier.classify(context.getEvent().content());
        } catch (RuntimeException e) {
            log.warn("ML 定级异常，按旧行为抑制: {}", e.toString());
            return AnalysisOutcome.suppress();
        }
        if (result.confidence() < confidenceThreshold || result.level() == AlertLevel.INFO) {
            return AnalysisOutcome.suppress();
        }
        context.setLevel(result.level());
        rescuedCount.incrementAndGet();
        log.info("ML 定级救援：级别 {}（置信度 {}），指纹 {}",
                result.level(), String.format("%.3f", result.confidence()), context.getFingerprint());
        return AnalysisOutcome.proceed();
    }

    /**
     * 累计救援事件数（正则漏判、被 ML 高置信定级放行的条数）。
     *
     * @return 救援计数
     */
    public long getRescuedCount() {
        return rescuedCount.get();
    }
}
