package com.smartops.infrastructure.sse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单个 SSE 会话的任务封装（阶段五 SSE 断线重连）。
 *
 * <p>每个 SseTask 绑定一个 conversationId，内部持有
 * {@link Sinks.Many} 热流，ChatClient 输出经该 Sink 广播到
 * 所有重连订阅者。任务结束时 Sink 终止，后续重连直接返回缓存结果。</p>
 *
 * <p>线程安全：start/complete 以 ReentrantLock 保护；Sink 本身线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class SseTask {

    /** 任务状态。 */
    public enum State { NEW, RUNNING, COMPLETED }

    private final Sinks.Many<String> sink;
    private final int bufferSize;
    private final ReentrantLock lock = new ReentrantLock();
    private final StringBuilder resultBuilder = new StringBuilder();

    private volatile State state = State.NEW;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile String finalResult;

    /**
     * 构造 SseTask。
     *
     * @param bufferSize Sinks 背压缓冲容量
     */
    public SseTask(int bufferSize) {
        this.bufferSize = bufferSize;
        this.sink = Sinks.many().multicast().onBackpressureBuffer(bufferSize, false);
    }

    /**
     * 启动任务：将源 Flux 桥接到内部 Sink。
     *
     * @param sourceFactory LLM 流式响应的冷 Flux 工厂
     * @throws IllegalStateException 当任务已启动时
     */
    public void start(Flux<String> sourceFactory) {
        lock.lock();
        try {
            if (state != State.NEW) {
                throw new IllegalStateException("任务已启动，当前状态: " + state);
            }
            state = State.RUNNING;
            startedAt = Instant.now();
        } finally {
            lock.unlock();
        }
        sourceFactory
                .doOnNext(chunk -> {
                    resultBuilder.append(chunk);
                    sink.emitNext(chunk, (signalType, emitResult) ->
                            emitResult == EmitResult.FAIL_NON_SERIALIZED
                                    || emitResult == EmitResult.FAIL_OVERFLOW);
                })
                .doOnComplete(() -> finish(null))
                .doOnError(e -> finish(e.getMessage()))
                .subscribe();
    }

    /**
     * 判断是否已完成。
     *
     * @return true 当状态为 COMPLETED
     */
    public boolean isCompleted() {
        return state == State.COMPLETED;
    }

    /**
     * 判断是否为新任务（未启动）。
     */
    public boolean isNew() {
        return state == State.NEW;
    }

    /**
     * 返回当前状态。
     */
    public State state() {
        return state;
    }

    /**
     * 启动时间，可为 null（未启动）。
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * 完成时间，可为 null（未完成）。
     */
    public Instant completedAt() {
        return completedAt;
    }

    /**
     * 最终累积结果（仅 COMPLETED 后有值）。
     */
    public String finalResult() {
        return finalResult;
    }

    /**
     * 订阅热流（从连接时刻起接收事件）。
     */
    public Flux<String> stream() {
        return sink.asFlux();
    }

    /**
     * 缓冲容量。
     */
    public int bufferSize() {
        return bufferSize;
    }

    /**
     * 完成任务：标记 COMPLETED，终止 Sink。
     */
    private void finish(String errorMessage) {
        lock.lock();
        try {
            if (state == State.COMPLETED) {
                return;
            }
            state = State.COMPLETED;
            completedAt = Instant.now();
            finalResult = resultBuilder.toString();
            if (errorMessage != null) {
                sink.emitError(new RuntimeException(errorMessage),
                        (signalType, emitResult) -> true);
            } else {
                sink.emitComplete((signalType, emitResult) -> true);
            }
        } finally {
            lock.unlock();
        }
    }
}
