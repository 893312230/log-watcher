package com.smartops.infrastructure.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;

/**
 * 有界工具执行资格检查器。
 *
 * <p>Spring AI 2.0 的 {@code ToolCallingAdvisor} 统一编排
 * "LLM 请求 → 工具调用 → 结果回传 → LLM 继续推理" 的循环，
 * 但<b>未提供内置的迭代上限配置</b>，恶意或失控的模型输出可能让
 * 工具调用循环无限进行。本检查器为每次请求的工具调用轮次设置上限：
 * 超过上限时判定响应不具备工具执行资格，循环终止，
 * 携带未执行工具调用的响应直接返回给调用方（优雅降级）。</p>
 *
 * <p><b>上限来源</b>（优先级从高到低）：
 * <ol>
 *   <li>当前线程通过 {@link ToolCallRoundGate#startRequest(int)} 设置的覆盖值
 *       （如 ReActExecutor 按请求传入的 maxIterations）</li>
 *   <li>构造时给定的默认上限（配置项 {@code smartops.react.max-tool-call-rounds}）</li>
 * </ol></p>
 *
 * <p><b>注意</b>：{@link #apply} 内不得调用 {@link #isToolCallResponse}——
 * 该默认方法内部又委托回 {@code apply}，直接调用会造成无限递归，
 * 因此这里直接使用 {@link ChatResponse#hasToolCalls()} 判定。</p>
 *
 * <p><b>轮次统计</b>：循环结束时（响应无工具调用或达到上限），
 * 将实际完成的轮次写入 {@link ToolCallRoundGate}，
 * 供调用方上报真实迭代次数。</p>
 *
 * <p>线程安全：轮次计数保存在 ThreadLocal 中；请求是同步单线程执行，
 * 且调用方（如 ReActExecutor）在 finally 中调用
 * {@link ToolCallRoundGate#clearRequest()}；本检查器的计数在循环结束时
 * 随之 remove，无跨请求泄露。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class BoundedToolExecutionEligibilityChecker implements ToolExecutionEligibilityChecker {

    private static final Logger log = LoggerFactory.getLogger(BoundedToolExecutionEligibilityChecker.class);

    /** 默认最大工具调用轮次（未被 {@link ToolCallRoundGate} 覆盖时生效）。 */
    private final int defaultMaxRounds;

    /** 当前请求已批准执行的工具调用轮次（线程隔离）。 */
    private final ThreadLocal<Integer> currentRound = new ThreadLocal<>();

    /**
     * 构造有界资格检查器。
     *
     * @param defaultMaxRounds 默认最大工具调用轮次，必须为正数
     * @throws IllegalArgumentException 当 {@code defaultMaxRounds} 非正数时
     */
    public BoundedToolExecutionEligibilityChecker(int defaultMaxRounds) {
        if (defaultMaxRounds <= 0) {
            throw new IllegalArgumentException("默认最大工具调用轮次必须为正数: " + defaultMaxRounds);
        }
        this.defaultMaxRounds = defaultMaxRounds;
    }

    /**
     * 判定该响应是否具备工具执行资格。
     *
     * <p>响应不含工具调用时：记录已完成轮次并返回 false（循环自然结束）。
     * 响应含工具调用时：轮次未超上限则放行（返回 true，Advisor 执行工具并继续循环），
     * 超过上限则记录轮次、打 warn 日志并返回 false（循环强制终止）。</p>
     *
     * @param response 模型响应，可为 null
     * @return true 表示应执行工具调用并继续循环；false 表示终止循环
     */
    @Override
    public Boolean apply(ChatResponse response) {
        if (response == null || !response.hasToolCalls()) {
            finishRound();
            return false;
        }

        int maxRounds = ToolCallRoundGate.effectiveMaxRounds(defaultMaxRounds);
        int nextRound = roundOrZero() + 1;
        if (nextRound > maxRounds) {
            log.warn("工具调用轮次达到上限 {}，终止工具执行循环（降级返回未执行工具调用的响应）", maxRounds);
            finishRound();
            return false;
        }

        currentRound.set(nextRound);
        return true;
    }

    /**
     * 返回默认最大工具调用轮次。
     *
     * @return 构造时给定的默认上限
     */
    public int getDefaultMaxRounds() {
        return defaultMaxRounds;
    }

    /**
     * 当前线程已批准的工具调用轮次，未开始请求时为 0。
     *
     * @return 当前轮次计数
     */
    private int roundOrZero() {
        Integer round = currentRound.get();
        return round != null ? round : 0;
    }

    /**
     * 结束当前工具循环：记录已完成轮次并重置线程计数。
     */
    private void finishRound() {
        ToolCallRoundGate.recordCompletedRounds(roundOrZero());
        currentRound.remove();
    }
}
