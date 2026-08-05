package com.smartops.common.exception;

/**
 * LLM 调用限流异常（阶段五韧性增强）。
 *
 * <p>ChatService 在每分钟 LLM 调用数超过上限时抛出本异常，
 * 错误码 {@code LLM_RATE_LIMIT}，上层组件可据此降级或返回系统繁忙提示。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class RateLimitException extends AgentException {

    /** 限流错误码。 */
    public static final String ERROR_CODE = "LLM_RATE_LIMIT";

    /**
     * 构造限流异常。
     *
     * @param message 错误消息（包含限流窗口与上限信息）
     */
    public RateLimitException(String message) {
        super(ERROR_CODE, message);
    }
}
