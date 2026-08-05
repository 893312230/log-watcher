package com.smartops.common.exception;

/**
 * Agent 平台基础异常。
 *
 * <p>所有业务异常的基类，对应 agent.md 第五章 5.3 节异常分层规范。
 * 各模块派生领域异常时必须继承本类，禁止直接抛出 {@link RuntimeException}。</p>
 *
 * <p>携带错误码与错误消息，便于上层统一处理与国际化。</p>
 *
 * <p>线程安全：异常对象一旦创建即不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class AgentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码，用于标识具体的错误类型。
     */
    private final String errorCode;

    /**
     * 构造一个带错误码和消息的异常。
     *
     * @param errorCode 错误码，不能为 null
     * @param message   错误描述信息
     */
    public AgentException(String errorCode, String message) {
        super(message);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("错误码不能为空");
        }
        this.errorCode = errorCode;
    }

    /**
     * 构造一个带错误码、消息和根因的异常。
     *
     * @param errorCode 错误码，不能为 null
     * @param message   错误描述信息
     * @param cause     根因异常
     */
    public AgentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("错误码不能为空");
        }
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码字符串
     */
    public String getErrorCode() {
        return errorCode;
    }
}
