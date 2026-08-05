package com.smartops.common.model;

import com.smartops.common.enums.AgentMode;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Agent 执行结果。
 *
 * <p>ReAct 或 Plan-and-Solve 执行器完成后的统一返回格式，
 * 包含最终答案、执行模式、迭代次数、步骤记录和执行状态。</p>
 *
 * <p>线程安全：字段不可变，steps 为不可变 List。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param answer       最终答案
 * @param mode         使用的执行模式
 * @param iterations   实际迭代/步骤数
 * @param steps        执行步骤记录
 * @param success      是否成功完成
 * @param errorMessage 失败时的错误信息，成功时为 null
 */
public record AgentExecutionResult(
        String answer,
        AgentMode mode,
        int iterations,
        List<String> steps,
        boolean success,
        String errorMessage
) {

    /**
     * 紧凑构造器：对 steps 做防御性拷贝。
     *
     * @param answer       最终答案
     * @param mode         执行模式
     * @param iterations   迭代次数
     * @param steps        步骤记录
     * @param success      是否成功
     * @param errorMessage 错误信息
     */
    public AgentExecutionResult {
        Objects.requireNonNull(mode, "执行模式不能为 null");
        steps = steps == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(List.copyOf(steps));
    }

    /**
     * 创建一个成功的执行结果。
     *
     * @param answer     最终答案
     * @param mode       执行模式
     * @param iterations 迭代次数
     * @param steps      步骤记录
     * @return 成功的执行结果
     */
    public static AgentExecutionResult success(String answer, AgentMode mode,
                                                int iterations, List<String> steps) {
        return new AgentExecutionResult(answer, mode, iterations, steps, true, null);
    }

    /**
     * 创建一个失败的执行结果。
     *
     * @param mode         执行模式
     * @param iterations   已执行的迭代次数
     * @param steps        已执行的步骤记录
     * @param errorMessage 错误信息
     * @return 失败的执行结果
     */
    public static AgentExecutionResult failure(AgentMode mode, int iterations,
                                                List<String> steps, String errorMessage) {
        return new AgentExecutionResult(null, mode, iterations, steps, false, errorMessage);
    }
}
