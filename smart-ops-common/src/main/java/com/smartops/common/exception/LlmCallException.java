package com.smartops.common.exception;

/**
 * LLM 调用失败异常。
 *
 * <p>对应 agent.md 第五章 5.3 节异常分层规范。由基础设施层
 * （{@code ChatService}）在 LLM 调用边界捕获 Spring AI / HTTP 层抛出的
 * 原始运行时异常后包装抛出，使上层组件（ReActExecutor、PlanAndSolveExecutor、
 * AgentRouter 等）可以类型化捕获平台异常，而非裸 catch {@link Exception}。</p>
 *
 * <p>固定错误码 {@value #ERROR_CODE}。</p>
 *
 * <p>线程安全：异常对象一旦创建即不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class LlmCallException extends AgentException {

    private static final long serialVersionUID = 1L;

    /** 固定错误码：LLM 调用失败。 */
    public static final String ERROR_CODE = "LLM_CALL_FAILED";

    /**
     * 构造 LLM 调用失败异常。
     *
     * @param message 错误描述信息
     */
    public LlmCallException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 构造 LLM 调用失败异常（带根因）。
     *
     * @param message 错误描述信息
     * @param cause   原始异常（Spring AI / HTTP 层异常）
     */
    public LlmCallException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
