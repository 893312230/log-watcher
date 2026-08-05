package com.smartops.common.enums;

/**
 * 运维意图类型枚举。
 *
 * <p>对应 agent.md 阶段二四层意图识别体系的输出分类。
 * 每种意图类型关联一组典型动词和场景，供 L1 正则、L2 动作词统计、
 * L3 ML 分类器、L4 LLM 兜底共同识别。</p>
 *
 * <p>线程安全：枚举天然不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public enum IntentType {

    /** 查询指标：如"查询 CPU 使用率""当前内存多少"。单步骤、只读、实时性高。 */
    QUERY_METRIC("查询指标", 1),

    /** 趋势分析：如"分析最近一小时 CPU 趋势""对比昨天和今天的 QPS"。多数据点、需聚合。 */
    TREND_ANALYSIS("趋势分析", 2),

    /** 分析告警：如"分析这个告警的原因""这个告警严重吗"。需关联多维度数据。 */
    ANALYZE_ALERT("分析告警", 3),

    /** 根因分析：如"为什么服务响应变慢""定位故障根因"。多步骤、探索性强。 */
    ROOT_CAUSE("根因分析", 4),

    /** 执行运维操作：如"重启服务""扩缩容""修改配置"。高风险、需人工确认。 */
    EXECUTE_OPERATION("执行运维操作", 5),

    /** 知识库问答：如"如何配置 Nginx""Nginx 负载均衡最佳实践"。只读、依赖知识检索。 */
    KNOWLEDGE_QA("知识库问答", 6),

    /** 未知意图：所有识别器均无法分类时的兜底结果。 */
    UNKNOWN("未知意图", 0),

    ;

    /** 意图的中文名称，用于日志展示与 Prompt 构造。 */
    private final String displayName;

    /** 意图的数字编码，用于 ML 分类器的标签映射。 */
    private final int code;

    IntentType(String displayName, int code) {
        this.displayName = displayName;
        this.code = code;
    }

    /**
     * 获取意图的中文名称。
     *
     * @return 中文显示名
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取意图的数字编码。
     *
     * @return 数字编码
     */
    public int getCode() {
        return code;
    }

    /**
     * 根据数字编码查找意图类型。
     *
     * @param code 数字编码
     * @return 对应的意图类型，找不到时返回 {@link #UNKNOWN}
     */
    public static IntentType fromCode(int code) {
        for (IntentType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
