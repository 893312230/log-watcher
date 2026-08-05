package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.agent.logwatch.pipeline.AnalysisLayer;
import com.smartops.common.enums.AlertLevel;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;

import java.util.regex.Pattern;

/**
 * L1 关键字定级层。
 *
 * <p>用内置正则对日志内容定级：命中错误特征（ERROR/FATAL/SEVERE/Exception，
 * 大小写不敏感）定 ERROR；命中 WARN 定 WARN。其余未命中的处理取决于模式：</p>
 * <ul>
 *   <li>传统模式（默认）：定 INFO 并直接 SUPPRESS
 *       （普通信息日志不值得进入后续高成本分析层）</li>
 *   <li>defer 模式（阶段十五，ML 层开启时）：不写级别、放行待定，
 *       交由下游 {@code MlClassifyLayer} 裁决正则词表漏判的事件</li>
 * </ul>
 *
 * <p>线程安全：无状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class L1ClassifyLayer implements AnalysisLayer {

    /** 错误特征：独立单词的 ERROR/FATAL/SEVERE，或任意 Exception 字样。 */
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "(?i)\\b(error|fatal|severe)\\b|Exception");

    /** 警告特征：独立单词 WARN/WARNING。 */
    private static final Pattern WARN_PATTERN = Pattern.compile("(?i)\\bwarn(ing)?\\b");

    /** true 时未命中正则的事件不写级别直接放行，留给 ML 层裁决。 */
    private final boolean deferToMl;

    /**
     * 构造传统模式 L1 层（未命中即 INFO 抑制）。
     */
    public L1ClassifyLayer() {
        this(false);
    }

    /**
     * 构造 L1 层。
     *
     * @param deferToMl true 表示未命中正则时不定级放行，为 ML 层留裁决空间
     */
    public L1ClassifyLayer(boolean deferToMl) {
        this.deferToMl = deferToMl;
    }

    @Override
    public int order() {
        return 1;
    }

    @Override
    public AnalysisOutcome apply(AnalysisContext context) {
        context.markLayerReached(1);
        String content = context.getEvent().content();

        if (ERROR_PATTERN.matcher(content).find()) {
            context.setLevel(AlertLevel.ERROR);
            return AnalysisOutcome.proceed();
        }
        if (WARN_PATTERN.matcher(content).find()) {
            context.setLevel(AlertLevel.WARN);
            return AnalysisOutcome.proceed();
        }
        if (deferToMl) {
            return AnalysisOutcome.proceed();
        }
        context.setLevel(AlertLevel.INFO);
        return AnalysisOutcome.suppress();
    }
}
