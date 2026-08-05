package com.smartops.agent.logwatch.pipeline;

import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;

/**
 * 分析层接口。
 *
 * <p>日志告警分析管线（由低到高 L0→L5）的单层契约，
 * 实现位于 impl 子包：L0 抑制、L1 正则定级、L2 ML 定级、L3 知识库、
 * L4 LLM、L5 Supervisor。管线按 {@link #order()} 升序执行，层返回 SUPPRESS/COMPLETE 即终止。</p>
 *
 * <p>线程约束：层实例被分析线程单线程调用，但实现如需共享状态应自行保证线程安全
 * （如 L0 的指纹窗口表）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface AnalysisLayer {

    /**
     * 层序号（0-5），管线按升序执行。
     *
     * @return 层序号
     */
    int order();

    /**
     * 执行本层分析，读写上下文后返回裁决。
     *
     * @param context 分析上下文（跨层累积）
     * @return 层裁决
     */
    AnalysisOutcome apply(AnalysisContext context);
}
