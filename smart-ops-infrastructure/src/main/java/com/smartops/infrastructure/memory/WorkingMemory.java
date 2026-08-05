package com.smartops.infrastructure.memory;

import java.util.Optional;

/**
 * 工作记忆（Task Scratchpad）端口。
 *
 * <p>对应 ADR-014 记忆分层的第三层：执行器在单次任务执行过程中读写中间状态
 * （ReAct 的 Thought/步骤记录、Plan-and-Solve 的步骤结果），任务结束后清理。
 * 与短期记忆（对话窗口）不同，工作记忆面向"执行过程"，不要求跨重启存活。</p>
 *
 * <p><b>隔离约定</b>：按 {@code conversationId} 一级隔离，会话内以 key 区分条目；
 * 调用方以带前缀的 key 区分执行器命名空间（如 {@code react.steps}、{@code plan.steps}）。</p>
 *
 * <p><b>降级约定</b>：工作记忆为可选组件（smartops.memory.working.enabled=false 时
 * 无 Bean），使用方通过 {@code ObjectProvider<WorkingMemory>} 注入，
 * 缺失时跳过读写，不影响主流程。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface WorkingMemory {

    /**
     * 写入键值；同 conversationId + key 覆盖旧值。
     *
     * @param conversationId 会话 ID（不能为 null 或空白）
     * @param key            条目 key（不能为 null）
     * @param value          值（不能为 null）
     */
    void put(String conversationId, String key, String value);

    /**
     * 读取会话内指定 key 的值。
     *
     * @param conversationId 会话 ID
     * @param key            条目 key
     * @return 值；会话或 key 不存在时为 {@link Optional#empty()}
     */
    Optional<String> get(String conversationId, String key);

    /**
     * 清理指定会话的全部工作记忆条目；会话不存在时静默忽略。
     *
     * @param conversationId 会话 ID
     */
    void clear(String conversationId);
}
