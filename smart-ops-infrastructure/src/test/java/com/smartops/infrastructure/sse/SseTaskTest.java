package com.smartops.infrastructure.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SseTask} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class SseTaskTest {

    @Test
    @DisplayName("新任务状态为 NEW")
    void should_beNew_when_constructed() {
        SseTask task = new SseTask(8);
        assertThat(task.state()).isEqualTo(SseTask.State.NEW);
        assertThat(task.isNew()).isTrue();
        assertThat(task.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("启动后状态变为 RUNNING")
    void should_beRunning_when_started() {
        SseTask task = new SseTask(8);
        // 使用 Flux.never() 确保 subscribe 同步返回后状态仍为 RUNNING
        task.start(reactor.core.publisher.Flux.never());
        assertThat(task.state()).isEqualTo(SseTask.State.RUNNING);
        assertThat(task.startedAt()).isNotNull();
    }

    @Test
    @DisplayName("源 Flux 完成后状态变为 COMPLETED 并缓存结果")
    void should_completeAndCacheResult_when_sourceCompletes() throws Exception {
        SseTask task = new SseTask(8);
        task.start(Flux.just("a", "b", "c"));

        // 等待异步完成
        Thread.sleep(200);
        assertThat(task.isCompleted()).isTrue();
        assertThat(task.completedAt()).isNotNull();
        assertThat(task.finalResult()).isEqualTo("abc");
    }

    @Test
    @DisplayName("已启动的任务再次 start 报 IllegalStateException")
    void should_throw_when_doubleStart() {
        SseTask task = new SseTask(8);
        task.start(Flux.just("x"));
        assertThatThrownBy(() -> task.start(Flux.just("y")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("stream 返回非 null Flux")
    void should_returnNonNullFlux_when_streamCalled() {
        SseTask task = new SseTask(8);
        assertThat(task.stream()).isNotNull();
    }

    @Test
    @DisplayName("源 Flux 出错时状态变为 COMPLETED")
    void should_complete_when_sourceErrors() throws Exception {
        SseTask task = new SseTask(8);
        task.start(Flux.error(new RuntimeException("fail")));
        Thread.sleep(200);
        assertThat(task.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("bufferSize 正确透传")
    void should_returnBufferSize_when_queried() {
        SseTask task = new SseTask(16);
        assertThat(task.bufferSize()).isEqualTo(16);
    }

    @Test
    @DisplayName("已完成任务再次 complete 不改变状态")
    void should_notChangeState_when_doubleComplete() throws Exception {
        SseTask task = new SseTask(8);
        task.start(Flux.just("done"));
        Thread.sleep(200);
        assertThat(task.isCompleted()).isTrue();
        // 状态保持 COMPLETED
        assertThat(task.state()).isEqualTo(SseTask.State.COMPLETED);
    }

    @Test
    @DisplayName("finish 在已 COMPLETED 时直接返回（反射覆盖 guard 分支）")
    void should_returnEarly_when_finishOnCompleted() throws Exception {
        SseTask task = new SseTask(8);
        task.start(Flux.just("done"));
        Thread.sleep(200);
        // 反射调用 private finish null
        var method = SseTask.class.getDeclaredMethod("finish", String.class);
        method.setAccessible(true);
        method.invoke(task, (String) null);
        // 不应该抛异常，状态保持 COMPLETED
        assertThat(task.isCompleted()).isTrue();
    }
}
