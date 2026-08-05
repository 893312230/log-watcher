package com.smartops.agent.intent;

import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * L2 动作词统计意图识别器。
 *
 * <p>对应 agent.md 阶段二任务2。基于关键词频率（TF-IDF 思想的简化版），
 * 统计用户输入中各意图类型的动作词命中次数，取命中最多者为识别结果。</p>
 *
 * <p>与 L1 正则的区别：L1 依赖严格的模式匹配，L2 基于词频统计更灵活。
 * 例如"帮我看看 CPU 和内存的情况，顺便分析下趋势"这种复合输入，
 * L1 可能无法精确匹配，L2 可以通过词频统计判断主导意图。</p>
 *
 * <p>置信度计算：0.3 基数 + 0.5×命中率²，上限 0.79（严格低于 L1 具体规则的 0.9）。
 * 多个意图命中数相同时，取优先级更高的（按 IntentType.code 降序）。</p>
 *
 * <p>线程安全：词表不可变，组件单例，线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class L2KeywordRecognizer implements IntentRecognizer {

    /** L2 识别的最大置信度上限：严格低于 L1 具体规则（0.9），避免越级短路。 */
    private static final double MAX_CONFIDENCE = 0.79;

    /** L2 置信度基数：任何命中都先给 0.3 基础分，再按命中率平方加权。 */
    private static final double BASE_CONFIDENCE = 0.3;

    /** L2 命中率平方的加权系数：命中率越高增长越快，惩罚稀疏命中。 */
    private static final double HIT_RATE_WEIGHT = 0.5;

    /** L2 无任何命中时的默认置信度。 */
    private static final double NO_MATCH_CONFIDENCE = 0.15;

    /** 各意图类型的关键词表。 */
    private final Map<IntentType, String[]> keywordTable;

    /**
     * 构造 L2 识别器，初始化关键词表。
     */
    public L2KeywordRecognizer() {
        keywordTable = new EnumMap<>(IntentType.class);
        keywordTable.put(IntentType.QUERY_METRIC,
                new String[]{"查询", "查看", "当前", "多少", "状态", "使用率", "占用", "cpu", "内存", "磁盘", "qps"});
        keywordTable.put(IntentType.TREND_ANALYSIS,
                new String[]{"趋势", "对比", "变化", "波动", "历史", "曲线", "走势", "最近", "小时", "分钟"});
        keywordTable.put(IntentType.ANALYZE_ALERT,
                new String[]{"告警", "报警", "alert", "异常", "故障", "分析", "排查", "严重", "级别"});
        keywordTable.put(IntentType.ROOT_CAUSE,
                new String[]{"为什么", "根因", "原因", "导致", "引起", "定位", "溯源", "排查"});
        keywordTable.put(IntentType.EXECUTE_OPERATION,
                new String[]{"重启", "扩容", "缩容", "修改", "配置", "部署", "回滚", "清理", "停止", "启动"});
        keywordTable.put(IntentType.KNOWLEDGE_QA,
                new String[]{"如何", "怎么", "怎样", "最佳实践", "文档", "教程", "方法", "指南", "手册"});
    }

    @Override
    public IntentResult recognize(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }

        String normalizedInput = userInput.toLowerCase();
        IntentType bestType = IntentType.UNKNOWN;
        int bestHitCount = 0;
        int bestTotalWords = 1;

        for (Map.Entry<IntentType, String[]> entry : keywordTable.entrySet()) {
            String[] keywords = entry.getValue();
            int hitCount = 0;
            for (String keyword : keywords) {
                if (normalizedInput.contains(keyword.toLowerCase())) {
                    hitCount++;
                }
            }
            // 命中数相同时，优先取 code 更大的（更具体的意图）
            if (hitCount > bestHitCount || (hitCount == bestHitCount && hitCount > 0
                    && entry.getKey().getCode() > bestType.getCode())) {
                bestHitCount = hitCount;
                bestTotalWords = keywords.length;
                bestType = entry.getKey();
            }
        }

        if (bestHitCount == 0) {
            return new IntentResult(IntentType.UNKNOWN, NO_MATCH_CONFIDENCE, getLayer(), null);
        }

        // 置信度映射：0.3 基数 + 0.5×命中率²，上限 0.79——
        // 严格位于 L1 宽泛兜底（0.4）与 L1 具体规则（0.9）之间，
        // 使三层置信度可比较，加权投票有意义
        double hitRate = (double) bestHitCount / bestTotalWords;
        double confidence = Math.min(BASE_CONFIDENCE + HIT_RATE_WEIGHT * hitRate * hitRate, MAX_CONFIDENCE);
        return new IntentResult(bestType, confidence, getLayer(), null);
    }

    @Override
    public String getLayer() {
        return IntentResult.SOURCE_L2_KEYWORD;
    }
}
