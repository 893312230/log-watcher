package com.smartops.common.exception;

/**
 * 安全违规异常。
 *
 * <p>当用户输入或 LLM 输出触发安全控制规则（如 Prompt 注入、越权调用、
 * 高风险操作未通过人工确认）时抛出。对应 agent.md 第九章 9.1 节
 * "Prompt 注入防护"的多层防御机制。</p>
 *
 * <p>此类异常一旦抛出，任务应立即终止并记录安全审计日志。</p>
 *
 * <p>线程安全：异常对象一旦创建即不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class SecurityViolationException extends AgentException {

    private static final long serialVersionUID = 1L;

    /**
     * 安全违规的默认错误码前缀。所有安全相关错误码以 SECURITY_ 开头。
     */
    public static final String ERROR_CODE_PREFIX = "SECURITY_";

    /**
     * 构造一个安全违规异常。
     *
     * @param errorCode 错误码，建议以 SECURITY_ 开头，不能为 null
     * @param message   违规详情
     */
    public SecurityViolationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造一个带根因的安全违规异常。
     *
     * @param errorCode 错误码，建议以 SECURITY_ 开头，不能为 null
     * @param message   违规详情
     * @param cause     根因异常
     */
    public SecurityViolationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
