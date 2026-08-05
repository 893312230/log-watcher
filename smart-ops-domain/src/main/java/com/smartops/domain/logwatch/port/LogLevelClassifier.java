package com.smartops.domain.logwatch.port;

import com.smartops.domain.logwatch.ClassificationResult;

/**
 * 日志级别机器学习分类器端口（阶段十五，ADR-019）。
 *
 * <p>对单条日志内容做三分类（ERROR/WARN/INFO），实现位于 infrastructure。
 * 降级契约（与 {@code KnowledgeRetriever} 一致）：任何内部异常、模型未就绪
 * 均不得抛出，返回 {@link ClassificationResult#abstain()} 弃权结果，
 * 由调用方（MlClassifyLayer）决定回退行为。</p>
 *
 * <p>线程安全：实现须在训练完成后只对模型做只读推理，可被多线程调用。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface LogLevelClassifier {

    /**
     * 对日志内容定级。
     *
     * @param content 日志原文（单行或多行）
     * @return 定级结果；分类器未就绪或推理失败时返回弃权结果
     */
    ClassificationResult classify(String content);

    /**
     * 模型是否就绪（训练完成且留出集准确率达标）。
     *
     * @return true 表示 classify 结果可采信
     */
    boolean isReady();
}
