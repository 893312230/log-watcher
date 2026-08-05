package com.smartops.domain.logwatch;

import com.smartops.common.enums.AlertLevel;

/**
 * 机器学习定级结果（阶段十五）。
 *
 * <p>承载 ML 分类器对单条日志的预测：级别 + 置信度（0.0-1.0）。
 * 置信度低于管线阈值时调用方应回退旧行为（抑制），不得盲目采信。</p>
 *
 * @param level      预测级别
 * @param confidence 预测置信度（0.0-1.0）
 * @author smartops
 * @since 1.0.0
 */
public record ClassificationResult(AlertLevel level, double confidence) {

    /**
     * 弃权结果：分类器未就绪或推理失败时返回，
     * 语义等价于"不做任何判断"（INFO + 零置信）。
     *
     * @return 弃权定级结果
     */
    public static ClassificationResult abstain() {
        return new ClassificationResult(AlertLevel.INFO, 0.0);
    }
}
