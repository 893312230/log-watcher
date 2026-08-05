package com.smartops.agent.security;

/**
 * 人工确认上下文（按线程隔离）。
 *
 * <p>API 层在验证一次性确认令牌后，将当前请求线程标记为"已确认"；
 * {@link SecurityGate} 在执行高危操作时读取该标记决定是否放行。
 * 请求结束后 API 层必须调用 {@link #clear()}，防止线程复用导致标记泄露
 * （Tomcat 线程池复用场景下，未清除的标记会让后续请求被误认为已确认）。</p>
 *
 * <p>同步请求处理模型的简化实现：当前 Supervisor 顺序执行子任务，
 * 全部在请求线程内完成，ThreadLocal 足以覆盖。若未来引入异步分发，
 * 需替换为显式上下文透传。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public final class ConfirmationContext {

    /** 当前线程的人工确认标记。 */
    private static final ThreadLocal<Boolean> CONFIRMED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ConfirmationContext() {
    }

    /**
     * 将当前线程标记为"已通过人工确认"。
     * 仅允许 API 层在验证确认令牌成功后调用。
     */
    public static void markConfirmed() {
        CONFIRMED.set(Boolean.TRUE);
    }

    /**
     * 判断当前线程是否已通过人工确认。
     *
     * @return 已确认返回 true
     */
    public static boolean isConfirmed() {
        return Boolean.TRUE.equals(CONFIRMED.get());
    }

    /**
     * 清除当前线程的确认标记。请求处理结束后必须调用（finally 块中）。
     */
    public static void clear() {
        CONFIRMED.remove();
    }
}
