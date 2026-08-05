package com.smartops.common.exception;

/**
 * 日志监控（logwatch）失败异常。
 *
 * <p>日志采集、关键字匹配、分析管线执行等 logwatch 链路边界
 * 捕获原始运行时异常后包装抛出，使上层可类型化捕获平台异常，
 * 对应 agent.md 第五章 5.3 节异常分层规范。</p>
 *
 * <p>固定错误码 {@value #ERROR_CODE}。</p>
 *
 * <p>线程安全：异常对象一旦创建即不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class LogWatchException extends AgentException {

    private static final long serialVersionUID = 1L;

    /** 固定错误码：日志监控失败。 */
    public static final String ERROR_CODE = "LOG_WATCH_FAILED";

    /**
     * 构造日志监控失败异常。
     *
     * @param message 错误描述信息
     */
    public LogWatchException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 构造日志监控失败异常（带根因）。
     *
     * @param message 错误描述信息
     * @param cause   原始异常（IO / 进程探测层异常）
     */
    public LogWatchException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
