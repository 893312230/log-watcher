package com.smartops.infrastructure.advisor;

/**
 * 工具调用轮次门（线程级）。
 *
 * <p>配合 {@link BoundedToolExecutionEligibilityChecker} 使用：
 * 调用方（如 ReActExecutor）在发起一次带工具的 LLM 请求前，
 * 通过 {@link #startRequest(int)} 声明本次请求允许的最大工具调用轮次；
 * 请求结束后（无论成功或异常）必须调用 {@link #clearRequest()} 清理，
 * 防止线程复用（如 Tomcat 线程池）导致状态泄露到无关请求。</p>
 *
 * <p><b>为什么用 ThreadLocal 而非方法参数透传</b>：
 * 工具调用循环发生在 Spring AI 的 ToolCallingAdvisor 内部，
 * 应用层无法在调用链上向资格检查器传递参数；而请求是同步单线程执行的，
 * ThreadLocal 足以在请求边界内传递配置与结果。</p>
 *
 * <p>线程安全：所有状态保存在 ThreadLocal 中，天然线程隔离。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public final class ToolCallRoundGate {

    /** 本次请求的最大工具调用轮次覆盖值（未设置时使用检查器默认值）。 */
    private static final ThreadLocal<Integer> MAX_ROUNDS_OVERRIDE = new ThreadLocal<>();

    /** 上一次请求实际完成的工具调用轮次（供调用方上报真实迭代次数）。 */
    private static final ThreadLocal<Integer> LAST_COMPLETED_ROUNDS = new ThreadLocal<>();

    private ToolCallRoundGate() {
        // 工具类，禁止实例化
    }

    /**
     * 开始一次带工具的 LLM 请求：设置最大轮次覆盖值并重置轮次记录。
     *
     * @param maxRounds 本次请求允许的最大工具调用轮次，必须为正数
     * @throws IllegalArgumentException 当 {@code maxRounds} 非正数时
     */
    public static void startRequest(int maxRounds) {
        if (maxRounds <= 0) {
            throw new IllegalArgumentException("最大工具调用轮次必须为正数: " + maxRounds);
        }
        MAX_ROUNDS_OVERRIDE.set(maxRounds);
        LAST_COMPLETED_ROUNDS.remove();
    }

    /**
     * 结束请求：清理本线程的全部轮次状态。必须在 finally 中调用。
     */
    public static void clearRequest() {
        MAX_ROUNDS_OVERRIDE.remove();
        LAST_COMPLETED_ROUNDS.remove();
    }

    /**
     * 返回本次请求生效的最大轮次（覆盖值优先，否则使用检查器默认值）。
     *
     * @param defaultMaxRounds 检查器默认上限
     * @return 生效的最大工具调用轮次
     */
    static int effectiveMaxRounds(int defaultMaxRounds) {
        Integer override = MAX_ROUNDS_OVERRIDE.get();
        return override != null ? override : defaultMaxRounds;
    }

    /**
     * 由资格检查器在一次请求的工具循环结束时记录实际完成的轮次。
     *
     * @param rounds 实际完成的工具调用轮次
     */
    static void recordCompletedRounds(int rounds) {
        LAST_COMPLETED_ROUNDS.set(rounds);
    }

    /**
     * 返回上一次请求实际完成的工具调用轮次（无记录时为 0）。
     *
     * @return 实际完成的工具调用轮次
     */
    public static int lastCompletedRounds() {
        Integer rounds = LAST_COMPLETED_ROUNDS.get();
        return rounds != null ? rounds : 0;
    }
}
