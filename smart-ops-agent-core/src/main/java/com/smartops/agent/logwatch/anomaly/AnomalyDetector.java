package com.smartops.agent.logwatch.anomaly;

import com.smartops.domain.logwatch.LogEvent;

/**
 * 异常检测器接口（阶段七 ML 异常检测引擎）。
 *
 * <p>对传入的 LogEvent 给出 0.0-1.0 的异常评分，
 * 评分高于阈值的视为异常事件，进入分析管线。</p>
 */
@FunctionalInterface
public interface AnomalyDetector {

    /**
     * 对事件评分。
     *
     * @param event 日志事件
     * @return 异常评分（0.0 正常，1.0 高度异常）
     */
    double score(LogEvent event);

    /** 默认评分阈值。 */
    default double threshold() { return 0.5; }
}
