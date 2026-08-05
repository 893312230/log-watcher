package com.smartops.agent.intent;

import com.smartops.common.model.IntentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 意图识别 Pipeline。
 *
 * <p>对应 agent.md 阶段二意图识别体系的编排入口。按 L1 → L2 → L4 顺序
 * 依次调用各层识别器，收集所有结果后由 {@link ConflictResolver} 加权投票
 * 得出最终意图。（原 L3 伪 ML 分类器已移除，见 ADR-011）</p>
 *
 * <p><b>执行流程</b>：
 * <ol>
 *   <li>L1 正则识别：具体规则置信度 0.9 ≥ 短路阈值（0.85）时直接返回；
 *       宽泛兜底规则仅 0.4，不参与短路</li>
 *   <li>L2 词频识别：与 L1 意图一致且达到通用置信阈值时直接返回</li>
 *   <li>L4 LLM 兜底：达到通用置信阈值时直接返回</li>
 *   <li>以上均不满足，由冲突解决器对三层结果加权投票</li>
 * </ol></p>
 *
 * <p><b>优化策略</b>：高置信度结果短路返回，避免不必要的 LLM 调用。</p>
 *
 * <p>线程安全：各识别器线程安全，组件单例，线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Service
public class IntentPipeline {

    private static final Logger log = LoggerFactory.getLogger(IntentPipeline.class);

    /**
     * L1 短路阈值：只有具体规则（0.9）能达到，宽泛兜底规则（0.4）
     * 与 L2 上限（0.79）均被排除在外，防止泛化匹配越级定论。
     */
    private static final double L1_SHORT_CIRCUIT_THRESHOLD = 0.85;

    /** L1 正则识别器。 */
    private final L1RegexRecognizer l1Recognizer;

    /** L2 词频识别器。 */
    private final L2KeywordRecognizer l2Recognizer;

    /** L4 LLM 兜底识别器。 */
    private final L4LLMRecognizer l4Recognizer;

    /** 冲突解决器。 */
    private final ConflictResolver conflictResolver;

    /**
     * 构造意图识别 Pipeline。
     *
     * @param l1Recognizer      L1 正则识别器
     * @param l2Recognizer      L2 词频识别器
     * @param l4Recognizer      L4 LLM 兜底识别器
     * @param conflictResolver  冲突解决器
     */
    public IntentPipeline(
            L1RegexRecognizer l1Recognizer,
            L2KeywordRecognizer l2Recognizer,
            L4LLMRecognizer l4Recognizer,
            ConflictResolver conflictResolver) {
        this.l1Recognizer = l1Recognizer;
        this.l2Recognizer = l2Recognizer;
        this.l4Recognizer = l4Recognizer;
        this.conflictResolver = conflictResolver;
    }

    /**
     * 执行三层意图识别。
     *
     * @param userInput 用户输入文本，不能为 null 或空白
     * @return 最终的意图识别结果
     * @throws IllegalArgumentException 当输入为 null 或空白时
     */
    public IntentResult recognize(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }

        log.info("开始意图识别: {}", userInput);
        List<IntentResult> results = new ArrayList<>(3);

        // L1 正则识别：仅具体规则（0.9）可短路，宽泛兜底（0.4）继续向下
        IntentResult l1Result = l1Recognizer.recognize(userInput);
        results.add(l1Result);
        log.debug("L1 正则识别结果: {} (置信度 {})", l1Result.intentType(), l1Result.confidence());

        if (l1Result.confidence() >= L1_SHORT_CIRCUIT_THRESHOLD) {
            log.info("L1 具体规则命中，短路返回: {}", l1Result.intentType());
            return l1Result;
        }

        // L2 词频识别
        IntentResult l2Result = l2Recognizer.recognize(userInput);
        results.add(l2Result);
        log.debug("L2 词频识别结果: {} (置信度 {})", l2Result.intentType(), l2Result.confidence());

        if (l2Result.isConfident() && l2Result.intentType() == l1Result.intentType()) {
            log.info("L1/L2 一致且高置信度，短路返回: {}", l2Result.intentType());
            return l2Result;
        }

        // L4 LLM 兜底
        IntentResult l4Result = l4Recognizer.recognize(userInput);
        results.add(l4Result);
        log.debug("L4 LLM 识别结果: {} (置信度 {})", l4Result.intentType(), l4Result.confidence());

        if (l4Result.isConfident()) {
            log.info("L4 LLM 兜底命中，返回: {}", l4Result.intentType());
            return l4Result;
        }

        // 多层结果冲突，交给冲突解决器
        IntentResult resolved = conflictResolver.resolve(results);
        log.info("冲突解决器最终结果: {} (置信度 {})", resolved.intentType(), resolved.confidence());
        return resolved;
    }

    /**
     * 获取所有识别器（用于测试和诊断）。
     *
     * @return 三层识别器列表
     */
    public List<IntentRecognizer> getRecognizers() {
        return List.of(l1Recognizer, l2Recognizer, l4Recognizer);
    }
}
