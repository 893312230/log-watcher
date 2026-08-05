package com.smartops.agent.intent;

import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 意图识别冲突解决器。
 *
 * <p>对应 agent.md 阶段二任务5。当多个识别器（L1/L2/L4）返回不同意图类型时，
 * 通过加权投票机制解决冲突，选出最终意图。（原 L3 伪 ML 分类器已移除，见 ADR-011）</p>
 *
 * <p><b>加权策略</b>：不同层级的识别器有不同的权重，与重设计后的置信度区间
 * 配套（L1 具体 0.9 / L2 ≤0.79 / L1 宽泛 0.4 / L4 真实置信度）：
 * L1 规则命中是确定性证据权重最高，L4 LLM 语义理解次之，L2 词频统计最弱。</p>
 * <ul>
 *   <li>L1 正则：权重 1.0（精确匹配，确定性最强）</li>
 *   <li>L2 词频：权重 0.7（灵活但可能误判）</li>
 *   <li>L4 LLM：权重 0.9（语义理解强，作为兜底权威）</li>
 * </ul>
 *
 * <p><b>投票逻辑</b>：对每个意图类型，累加所有投该票的识别器的 (权重 × 置信度)。
 * 取累加分数最高的意图为最终结果，最终置信度 = 归一化加权平均
 * （该意图加权分数总和 / 投该票的识别器权重总和，上限 1.0）。</p>
 *
 * <p>线程安全：无状态，组件单例，线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class ConflictResolver {

    /** 各层级的权重配置。 */
    private final Map<String, Double> layerWeights;

    /**
     * 构造冲突解决器，初始化层级权重。
     */
    public ConflictResolver() {
        layerWeights = Map.of(
                IntentResult.SOURCE_L1_REGEX, 1.0,
                IntentResult.SOURCE_L2_KEYWORD, 0.7,
                IntentResult.SOURCE_L4_LLM, 0.9
        );
    }

    /**
     * 对多个识别器的结果做加权投票，选出最终意图。
     *
     * @param results 各层识别器的结果列表，不能为 null 或空
     * @return 加权投票后的最终意图结果
     * @throws IllegalArgumentException 当结果列表为空时
     */
    public IntentResult resolve(List<IntentResult> results) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("识别结果列表不能为空");
        }

        // 按意图类型分组，累加加权分数
        Map<IntentType, Double> scores = results.stream()
                .filter(r -> r.intentType() != IntentType.UNKNOWN)
                .collect(Collectors.groupingBy(
                        IntentResult::intentType,
                        Collectors.summingDouble(r -> r.confidence() * getWeight(r.source()))
                ));

        if (scores.isEmpty()) {
            // 所有识别器都返回 UNKNOWN
            return IntentResult.unknown();
        }

        // 取分数最高的意图
        Map.Entry<IntentType, Double> best = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();

        // 计算最终置信度：加权分数 / 权重总和
        double totalWeight = results.stream()
                .filter(r -> r.intentType() == best.getKey())
                .mapToDouble(r -> getWeight(r.source()))
                .sum();

        double finalConfidence = totalWeight > 0
                ? Math.min(best.getValue() / totalWeight, 1.0)
                : best.getValue();

        // 记录投票来源
        String sources = results.stream()
                .filter(r -> r.intentType() == best.getKey())
                .map(IntentResult::source)
                .collect(Collectors.joining(","));

        return new IntentResult(best.getKey(), finalConfidence, "VOTED:" + sources, null);
    }

    /**
     * 获取指定层级的权重。
     *
     * @param source 识别来源层级
     * @return 权重值，未配置的层级默认 0.1
     */
    private double getWeight(String source) {
        return layerWeights.getOrDefault(source, 0.1);
    }
}
