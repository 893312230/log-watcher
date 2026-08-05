package com.smartops.common.model;

import com.smartops.common.enums.IntentType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 意图识别结果。
 *
 * <p>三层意图识别器（L1 正则 / L2 词频 / L4 LLM）的统一输出格式，记录识别出的意图类型、
 * 置信度、识别来源以及从用户输入中提取的实体信息。</p>
 *
 * <p>冲突解决器根据置信度和来源层级对多个识别结果做加权投票，
 * 最终输出一个 {@code IntentResult} 供路由决策引擎使用。</p>
 *
 * <p>线程安全：字段不可变（entities 为不可变 Map），对象创建后状态不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param intentType  识别出的意图类型
 * @param confidence  置信度，范围 0.0-1.0，1.0 表示完全确定
 * @param source      识别来源层级（L1/L2/L4）
 * @param extractedEntities 从用户输入中提取的实体，如 metricName、timeRange 等，可能为空 Map
 */
public record IntentResult(
        IntentType intentType,
        double confidence,
        String source,
        Map<String, String> extractedEntities
) {

    /** 识别来源层级常量：L1 正则规则。 */
    public static final String SOURCE_L1_REGEX = "L1_REGEX";

    /** 识别来源层级常量：L2 动作词统计。 */
    public static final String SOURCE_L2_KEYWORD = "L2_KEYWORD";

    /** 识别来源层级常量：L4 LLM 兜底。 */
    public static final String SOURCE_L4_LLM = "L4_LLM";

    /** 默认置信度阈值：低于此值的识别结果视为低置信度，需进入下一层。 */
    public static final double CONFIDENCE_THRESHOLD = 0.6;

    /**
     * 紧凑构造器：对实体 Map 做防御性拷贝，确保不可变性。
     *
     * @param intentType  意图类型
     * @param confidence  置信度
     * @param source      识别来源
     * @param extractedEntities 提取的实体，null 时视为空 Map
     */
    public IntentResult {
        Objects.requireNonNull(intentType, "意图类型不能为 null");
        Objects.requireNonNull(source, "识别来源不能为 null");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("置信度必须在 0.0-1.0 之间，实际: " + confidence);
        }
        extractedEntities = extractedEntities == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(extractedEntities));
    }

    /**
     * 判断置信度是否达到阈值。
     *
     * @return 若置信度 >= {@link #CONFIDENCE_THRESHOLD} 返回 true
     */
    public boolean isConfident() {
        return confidence >= CONFIDENCE_THRESHOLD;
    }

    /**
     * 创建一个低置信度的未知意图结果（用于所有识别器均失败时的兜底）。
     *
     * @return 意图类型为 UNKNOWN、置信度为 0、来源为空的 IntentResult
     */
    public static IntentResult unknown() {
        return new IntentResult(IntentType.UNKNOWN, 0.0, "NONE", null);
    }

    /**
     * 创建一个带意图类型和置信度的简化结果。
     *
     * @param intentType 意图类型
     * @param confidence 置信度
     * @param source     识别来源
     * @return 不含实体的 IntentResult
     */
    public static IntentResult of(IntentType intentType, double confidence, String source) {
        return new IntentResult(intentType, confidence, source, null);
    }
}
