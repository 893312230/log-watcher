package com.smartops.common.enums;

/**
 * 安全等级枚举。
 *
 * <p>对应 agent.md 第三章 3.3 节"四级安全控制"，
 * 每一级在前一级基础上叠加更严格的控制措施。</p>
 *
 * <ul>
 *   <li>{@link #L0_INPUT_FILTER} - 输入过滤：SQL 注入、XSS、命令注入等传统安全过滤</li>
 *   <li>{@link #L1_PERMISSION_CHECK} - 权限校验：基于 RBAC 的工具调用权限控制</li>
 *   <li>{@link #L2_AUDIT_LOG} - 操作审计：所有工具调用和 LLM 输出的完整审计日志</li>
 *   <li>{@link #L3_HUMAN_CONFIRM} - 人工确认：高风险操作（如重启服务）需人工确认</li>
 * </ul>
 *
 * <p>等级数值越高，控制越严格。L3 包含 L0/L1/L2 的全部控制。</p>
 *
 * <p>线程安全：枚举天然不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public enum SecurityLevel {

    /**
     * L0：输入过滤。对所有用户输入进行传统安全过滤。
     */
    L0_INPUT_FILTER(0),

    /**
     * L1：权限校验。在 L0 基础上，校验调用方是否有权使用目标工具。
     */
    L1_PERMISSION_CHECK(1),

    /**
     * L2：操作审计。在 L1 基础上，记录完整的工具调用与 LLM 输出审计日志。
     */
    L2_AUDIT_LOG(2),

    /**
     * L3：人工确认。在 L2 基础上，对高风险操作要求人工二次确认后方可执行。
     */
    L3_HUMAN_CONFIRM(3),

    ;

    private final int level;

    SecurityLevel(int level) {
        this.level = level;
    }

    /**
     * 获取安全等级的数值，数值越大控制越严格。
     *
     * @return 等级数值（0-3）
     */
    public int getLevel() {
        return level;
    }

    /**
     * 判断当前等级是否覆盖了目标等级。
     *
     * <p>等级数值大的覆盖等级数值小的。例如 L3 覆盖 L0/L1/L2。</p>
     *
     * @param target 待判断的目标等级，不能为 null
     * @return 若当前等级数值大于等于目标等级数值，返回 true
     */
    public boolean covers(SecurityLevel target) {
        if (target == null) {
            throw new IllegalArgumentException("目标安全等级不能为 null");
        }
        return this.level >= target.level;
    }
}
