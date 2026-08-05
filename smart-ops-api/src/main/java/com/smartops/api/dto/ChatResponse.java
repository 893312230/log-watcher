package com.smartops.api.dto;

import com.smartops.common.enums.AgentMode;

import java.time.LocalDateTime;

/**
 * 对话响应 DTO。
 *
 * <p>Agent 处理用户问题后返回的结果，包含回复内容、会话 ID、时间戳
 * 以及执行元数据（执行模式、迭代次数、成功状态、错误信息）。</p>
 *
 * <p>阶段二集成 AgentRouter 后，响应中携带路由决策与执行过程的元数据，
 * 便于客户端展示执行轨迹、调试路由逻辑、区分成功/失败场景。</p>
 *
 * <p>线程安全：DTO 不可变（record 天然不可变），线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param conversationId      会话 ID，后续对话需带上此 ID 以保持上下文
 * @param reply               Agent 的回复内容，执行失败或待确认时可能为 null
 * @param timestamp           响应时间戳
 * @param mode                执行模式（REACT / PLAN_AND_SOLVE）， null 表示未经过路由
 * @param iterations          执行迭代/步骤次数，0 表示未执行或失败
 * @param success             是否成功完成
 * @param errorMessage        失败时的错误信息，成功时为 null
 * @param pendingConfirmation 是否处于"待人工确认"状态：true 表示请求触发了
 *                            高危操作安全门，需客户端携带 confirmationToken 重提
 * @param confirmationToken   一次性确认令牌（TTL 10 分钟），仅在
 *                            pendingConfirmation=true 时非空
 */
public record ChatResponse(
        String conversationId,
        String reply,
        LocalDateTime timestamp,
        AgentMode mode,
        int iterations,
        boolean success,
        String errorMessage,
        boolean pendingConfirmation,
        String confirmationToken
) {

    /**
     * 便捷工厂方法：基于执行结果构建成功响应。
     *
     * @param conversationId 会话 ID
     * @param reply          回复内容
     * @param mode           执行模式
     * @param iterations     迭代次数
     * @return 成功的对话响应
     */
    public static ChatResponse success(String conversationId, String reply,
                                       AgentMode mode, int iterations) {
        return new ChatResponse(conversationId, reply, LocalDateTime.now(),
                mode, iterations, true, null, false, null);
    }

    /**
     * 便捷工厂方法：基于执行失败构建错误响应。
     *
     * @param conversationId 会话 ID
     * @param mode           执行模式
     * @param errorMessage   错误信息
     * @return 失败的对话响应
     */
    public static ChatResponse failure(String conversationId, AgentMode mode,
                                       String errorMessage) {
        return new ChatResponse(conversationId, null, LocalDateTime.now(),
                mode, 0, false, errorMessage, false, null);
    }

    /**
     * 便捷工厂方法：构建"待人工确认"响应。
     *
     * <p>高危操作被安全门拦截时返回，客户端需携带令牌与原始消息重提请求。</p>
     *
     * @param conversationId    会话 ID
     * @param confirmationToken 签发的一次性确认令牌
     * @param detail            安全门拦截原因（展示给用户）
     * @return 待确认的对话响应
     */
    public static ChatResponse pendingConfirmation(String conversationId,
                                                   String confirmationToken, String detail) {
        return new ChatResponse(conversationId, null, LocalDateTime.now(),
                null, 0, false, detail, true, confirmationToken);
    }
}
