package com.smartops.infrastructure.observability;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 带观测能力的 {@link ToolCallingManager} 装饰器（阶段五：工具调用指标+审计）。
 *
 * <p>包装 {@code DefaultToolCallingManager}，在
 * {@link #executeToolCalls(Prompt, ChatResponse)} 边界统一计时并记录：
 * 从 ChatResponse 的 AssistantMessage 提取本轮全部工具调用（名称+入参），
 * 逐条经 {@link Observability#recordToolCall} 写入指标与审计事件。
 * 一次 executeToolCalls 可能包含多个工具调用，全部成功记成功，
 * 抛出运行时异常则全部记失败并重抛，观测本身永不向业务链路抛异常。</p>
 *
 * <p>observability 为 null（测试或裁剪场景）时纯透传。</p>
 *
 * <p>线程安全：委托对象与 Observability 均线程安全，本类无状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class ObservedToolCallingManager implements ToolCallingManager {

    /** 工具入参摘要的最大长度（超出截断，避免大入参撑爆审计字段）。 */
    static final int ARGUMENTS_MAX_LENGTH = 500;

    private final ToolCallingManager delegate;
    private final Observability observability;
    private final Semaphore semaphore;
    private final long semaphoreTimeoutMs;

    /**
     * 构造观测装饰器（无并发控制）。
     */
    public ObservedToolCallingManager(ToolCallingManager delegate, Observability observability) {
        this(delegate, observability, 0, 0);
    }

    /**
     * 构造观测装饰器（含并发控制 Semaphore）。
     *
     * @param delegate           被包装的工具调用管理器
     * @param observability      可观测性门面，可为 null（纯透传）
     * @param maxConcurrent      最大并发数（≤0 表示不限并发）
     * @param semaphoreTimeoutMs 获取许可超时毫秒
     */
    public ObservedToolCallingManager(ToolCallingManager delegate, Observability observability,
                                      int maxConcurrent, long semaphoreTimeoutMs) {
        this.delegate = delegate;
        this.observability = observability;
        this.semaphore = maxConcurrent > 0 ? new Semaphore(maxConcurrent) : null;
        this.semaphoreTimeoutMs = semaphoreTimeoutMs;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        boolean acquired = acquireSemaphore();
        try {
            List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(chatResponse);
            long startNanos = System.nanoTime();
            try {
                ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);
                if (observability != null) {
                    recordAll(toolCalls, true, startNanos, null);
                }
                return result;
            } catch (RuntimeException e) {
                if (observability != null) {
                    recordAll(toolCalls, false, startNanos, e.getMessage());
                }
                throw e;
            }
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private boolean acquireSemaphore() {
        if (semaphore == null) {
            return false;
        }
        try {
            return semaphore.tryAcquire(semaphoreTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 从 ChatResponse 提取本轮全部工具调用；无则返回空列表。
     */
    private List<AssistantMessage.ToolCall> extractToolCalls(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResults() == null) {
            return Collections.emptyList();
        }
        return chatResponse.getResults().stream()
                .map(Generation::getOutput)
                .filter(output -> output != null && output.hasToolCalls())
                .flatMap(output -> output.getToolCalls().stream())
                .toList();
    }

    /**
     * 逐条记录工具调用观测；observability 内部对记录失败静默。
     */
    private void recordAll(List<AssistantMessage.ToolCall> toolCalls, boolean success,
                           long startNanos, String errorMessage) {
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        for (AssistantMessage.ToolCall call : toolCalls) {
            String detail = success ? truncate(call.arguments())
                    : "工具调用失败: " + errorMessage;
            observability.recordToolCall(call.name(), success, latencyMs, detail);
        }
    }

    /**
     * 截断工具入参摘要，超长追加省略标记。
     */
    private String truncate(String arguments) {
        if (arguments == null) {
            return "";
        }
        return arguments.length() <= ARGUMENTS_MAX_LENGTH ? arguments
                : arguments.substring(0, ARGUMENTS_MAX_LENGTH) + "...";
    }
}
