package com.smartops.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 对话请求 DTO。
 *
 * <p>用户通过 REST API 发起运维对话的请求体。
 * 支持指定会话 ID 以保持多轮对话上下文。</p>
 *
 * <p>线程安全：DTO 不可变（字段 final），线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param conversationId    会话 ID，用于关联短期记忆。为空时表示新会话
 * @param message           用户输入的运维问题，不能为空，最长 2000 字符
 * @param confirmationToken 高危操作人工确认令牌（可选）。当上一次请求返回
 *                          pendingConfirmation=true 时，客户端携带该令牌与原始消息
 *                          重提请求以确认执行；普通请求传 null
 */
public record ChatRequest(
        String conversationId,

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容不能超过 2000 字符")
        String message,

        String confirmationToken
) {
}
