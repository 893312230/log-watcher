package com.smartops.agent.security;

/**
 * L0 输入过滤器接口（阶段五安全模型）。
 *
 * <p>在 Agent 路由前对用户输入做安全清洗：阻断恶意输入（XSS/SQL/命令注入）
 * 或返回清洗后的安全文本。实现必须幂等且不依赖外部服务。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@FunctionalInterface
public interface InputFilter {

    /**
     * 过滤用户输入。
     *
     * @param rawInput 原始用户输入，可为 null 或空白
     * @return 清洗后的文本
     * @throws com.smartops.common.exception.SecurityViolationException
     *         当检测到高风险注入模式时
     */
    String filter(String rawInput);
}
