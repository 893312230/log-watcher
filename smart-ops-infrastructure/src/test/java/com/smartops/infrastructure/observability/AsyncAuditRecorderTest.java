package com.smartops.infrastructure.observability;

import com.smartops.common.enums.AuditEventType;
import com.smartops.domain.audit.AuditEvent;
import com.smartops.infrastructure.persistence.audit.impl.AuditRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AsyncAuditRecorder} 单元测试。
 *
 * <p>覆盖：异步落库、队列满丢弃计数、null 事件忽略、
 * 落库异常不扩散、停止时 drain 剩余事件、未启动时停止空转。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AsyncAuditRecorderTest {

    private AuditEvent event(String traceId) {
        return AuditEvent.create(AuditEventType.LLM_CALL, traceId, "actor",
                null, null, true, 1, Instant.now());
    }

    @Test
    @DisplayName("投递的事件被异步落库")
    void should_persistAsynchronously_when_started() throws Exception {
        List<AuditEvent> saved = new CopyOnWriteArrayList<>();
        AuditRepositoryImpl repository = mock(AuditRepositoryImpl.class);
        when(repository.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        AsyncAuditRecorder recorder = new AsyncAuditRecorder(repository, 16);
        recorder.start();
        try {
            recorder.record(event("t-1"));
            recorder.record(event("t-2"));

            long deadline = System.currentTimeMillis() + 5000;
            while (saved.size() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }

            assertThat(saved).hasSize(2);
            assertThat(recorder.getDroppedCount()).isZero();
        } finally {
            recorder.stop();
        }
    }

    @Test
    @DisplayName("未启动时队列满丢弃并计数，不抛异常")
    void should_dropAndCount_when_queueFull() {
        AsyncAuditRecorder recorder = new AsyncAuditRecorder(
                mock(AuditRepositoryImpl.class), 2);

        assertThatCode(() -> {
            for (int i = 0; i < 8; i++) {
                recorder.record(event("t-" + i));
            }
        }).doesNotThrowAnyException();

        assertThat(recorder.getDroppedCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("null 事件被忽略")
    void should_ignoreNull_when_recordNull() {
        AsyncAuditRecorder recorder = new AsyncAuditRecorder(
                mock(AuditRepositoryImpl.class), 2);

        assertThatCode(() -> recorder.record(null)).doesNotThrowAnyException();
        assertThat(recorder.getDroppedCount()).isZero();
    }

    @Test
    @DisplayName("落库异常不扩散，后续事件继续处理")
    void should_continueConsuming_when_saveFails() throws Exception {
        List<AuditEvent> saved = new CopyOnWriteArrayList<>();
        AtomicLong calls = new AtomicLong();
        AuditRepositoryImpl repository = mock(AuditRepositoryImpl.class);
        when(repository.save(any())).thenAnswer(inv -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("db down");
            }
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        AsyncAuditRecorder recorder = new AsyncAuditRecorder(repository, 16);
        recorder.start();
        try {
            recorder.record(event("bad"));
            recorder.record(event("good"));

            long deadline = System.currentTimeMillis() + 5000;
            while (saved.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }

            assertThat(saved).hasSize(1);
        } finally {
            recorder.stop();
        }
    }

    @Test
    @DisplayName("停止时 drain 队列剩余事件")
    void should_drainQueue_when_stopped() {
        AuditRepositoryImpl repository = mock(AuditRepositoryImpl.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AsyncAuditRecorder recorder = new AsyncAuditRecorder(repository, 16);
        recorder.start();
        recorder.record(event("t-1"));

        recorder.stop();

        verify(repository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("重复启动与未启动停止均为空转")
    void should_noOp_when_doubleStartOrStopWithoutStart() {
        AsyncAuditRecorder recorder = new AsyncAuditRecorder(
                mock(AuditRepositoryImpl.class), 2);

        assertThatCode(() -> {
            recorder.stop();
            recorder.start();
            recorder.start();
            recorder.stop();
            recorder.stop();
        }).doesNotThrowAnyException();
    }
}
