package com.smartops.common.enums;

/**
 * Agent 角色枚举。
 *
 * <p>对应 agent.md 阶段三 Multi-Agent 架构中的角色划分。
 * Supervisor-Worker 模式下，Supervisor 负责任务分解与结果聚合，
 * Worker 为各专业子 Agent，按运维领域分工。</p>
 *
 * <ul>
 *   <li>{@link #SUPERVISOR} - 主管 Agent：任务分解、Worker 分配、结果聚合</li>
 *   <li>{@link #MONITOR} - 监控 Agent：实时监控、告警查询、指标趋势分析</li>
 *   <li>{@link #ANALYZE} - 分析 Agent：根因分析、日志分析、异常检测</li>
 *   <li>{@link #EXECUTE} - 执行 Agent：自动化运维操作（重启、扩缩容、配置变更）</li>
 *   <li>{@link #KNOWLEDGE} - 知识 Agent：运维知识库问答、最佳实践推荐</li>
 * </ul>
 *
 * <p>线程安全：枚举天然不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public enum AgentRole {

    /** 主管 Agent：任务分解、Worker 分配、结果聚合。 */
    SUPERVISOR("主管", "负责任务分解、Worker 分配与结果聚合"),

    /** 监控 Agent：实时监控、告警查询、指标趋势分析。 */
    MONITOR("监控", "实时监控、告警查询、指标趋势分析"),

    /** 分析 Agent：根因分析、日志分析、异常检测。 */
    ANALYZE("分析", "根因分析、日志分析、异常检测"),

    /** 执行 Agent：自动化运维操作（重启、扩缩容、配置变更）。 */
    EXECUTE("执行", "自动化运维操作（重启、扩缩容、配置变更）"),

    /** 知识 Agent：运维知识库问答、最佳实践推荐。 */
    KNOWLEDGE("知识", "运维知识库问答、最佳实践推荐"),
    ;

    /** 角色的中文名称。 */
    private final String displayName;

    /** 角色的能力描述。 */
    private final String description;

    AgentRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 获取角色的中文名称。
     *
     * @return 中文显示名
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取角色的能力描述。
     *
     * @return 能力描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为 Worker 角色（非 Supervisor）。
     *
     * @return 如果是 Worker 角色返回 true
     */
    public boolean isWorker() {
        return this != SUPERVISOR;
    }
}
