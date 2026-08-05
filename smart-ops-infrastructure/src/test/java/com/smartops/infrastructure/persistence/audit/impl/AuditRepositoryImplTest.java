package com.smartops.infrastructure.persistence.audit.impl;

import com.smartops.common.enums.AuditEventType;
import com.smartops.domain.audit.AuditEvent;
import com.smartops.domain.audit.AuditEventPage;
import com.smartops.domain.audit.AuditEventQuery;
import com.smartops.infrastructure.persistence.audit.AuditEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditRepositoryImpl} 数据层集成测试（@DataJpaTest + H2）。
 *
 * <p>覆盖：保存往返、多条件过滤分页、时间范围过滤。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@DataJpaTest
class AuditRepositoryImplTest {

    private static final Instant T1 = Instant.parse("2026-07-23T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-23T11:00:00Z");

    @Autowired
    private AuditEventJpaRepository jpaRepository;

    private AuditRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new AuditRepositoryImpl(jpaRepository);
    }

    private AuditEvent newEvent(AuditEventType type, String traceId, Instant time) {
        return AuditEvent.create(type, traceId, "chatService", "kimi-for-coding",
                "详情 " + type, true, 100, time);
    }

    @Test
    @DisplayName("保存事件后分配 id，字段完整往返")
    void should_roundTripAllFields_when_saved() {
        AuditEvent saved = repository.save(newEvent(AuditEventType.LLM_CALL, "conv-1", T1));

        assertThat(saved.id()).isNotNull();
        AuditEventPage page = repository.query(new AuditEventQuery(
                AuditEventType.LLM_CALL, "conv-1", null, null, null, null, 0, 10));
        assertThat(page.total()).isEqualTo(1);
        AuditEvent found = page.items().get(0);
        assertThat(found.actor()).isEqualTo("chatService");
        assertThat(found.target()).isEqualTo("kimi-for-coding");
        assertThat(found.detail()).isEqualTo("详情 LLM_CALL");
        assertThat(found.success()).isTrue();
        assertThat(found.latencyMs()).isEqualTo(100);
        assertThat(found.createdAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("按类型与关联标识过滤，按发生时间倒序")
    void should_filterAndSort_when_querying() {
        repository.save(newEvent(AuditEventType.LLM_CALL, "conv-1", T1));
        repository.save(newEvent(AuditEventType.LLM_CALL, "conv-2", T2));
        repository.save(newEvent(AuditEventType.TOOL_CALL, "conv-1", T2));

        AuditEventPage llmOnly = repository.query(new AuditEventQuery(
                AuditEventType.LLM_CALL, null, null, null, null, null, 0, 10));
        assertThat(llmOnly.total()).isEqualTo(2);
        assertThat(llmOnly.items()).extracting(AuditEvent::traceId)
                .containsExactly("conv-2", "conv-1");

        AuditEventPage byTrace = repository.query(new AuditEventQuery(
                null, "conv-1", null, null, null, null, 0, 10));
        assertThat(byTrace.items()).extracting(AuditEvent::eventType)
                .containsExactly(AuditEventType.TOOL_CALL, AuditEventType.LLM_CALL);
    }

    @Test
    @DisplayName("按时间范围过滤")
    void should_filterByTimeRange_when_fromToGiven() {
        repository.save(newEvent(AuditEventType.LLM_CALL, "c", T1));
        repository.save(newEvent(AuditEventType.LLM_CALL, "c", T2));

        AuditEventPage fromOnly = repository.query(new AuditEventQuery(
                null, null, null, null, Instant.parse("2026-07-23T10:30:00Z"), null, 0, 10));
        assertThat(fromOnly.total()).isEqualTo(1);
        assertThat(fromOnly.items().get(0).createdAt()).isEqualTo(T2);

        AuditEventPage toOnly = repository.query(new AuditEventQuery(
                null, null, null, null, null, Instant.parse("2026-07-23T10:30:00Z"), 0, 10));
        assertThat(toOnly.total()).isEqualTo(1);
        assertThat(toOnly.items().get(0).createdAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("分页元信息正确返回")
    void should_returnPageMeta_when_paging() {
        for (int i = 0; i < 5; i++) {
            repository.save(newEvent(AuditEventType.TASK_EXECUTION, "c",
                    T1.plusSeconds(i * 60L)));
        }

        AuditEventPage page = repository.query(
                new AuditEventQuery(null, null, null, null, null, null, 1, 2));

        assertThat(page.total()).isEqualTo(5);
        assertThat(page.items()).hasSize(2);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("按发起者包含匹配过滤")
    void should_filterByActorContains_when_actorGiven() {
        repository.save(AuditEvent.create(AuditEventType.TASK_EXECUTION, "c1", "admin", "t",
                "d", true, 10, T1));
        repository.save(AuditEvent.create(AuditEventType.TASK_EXECUTION, "c2", "system", "t",
                "d", true, 10, T2));

        AuditEventPage page = repository.query(new AuditEventQuery(
                null, null, "adm", null, null, null, 0, 10));

        assertThat(page.items()).extracting(AuditEvent::actor).containsExactly("admin");
    }

    @Test
    @DisplayName("按操作结果过滤")
    void should_filterBySuccess_when_successGiven() {
        repository.save(AuditEvent.create(AuditEventType.LLM_CALL, "c1", "a", "t",
                "d", true, 10, T1));
        repository.save(AuditEvent.create(AuditEventType.LLM_CALL, "c2", "a", "t",
                "d", false, 10, T2));

        AuditEventPage failed = repository.query(new AuditEventQuery(
                null, null, null, false, null, null, 0, 10));
        AuditEventPage succeeded = repository.query(new AuditEventQuery(
                null, null, null, true, null, null, 0, 10));

        assertThat(failed.items()).extracting(AuditEvent::traceId).containsExactly("c2");
        assertThat(succeeded.items()).extracting(AuditEvent::traceId).containsExactly("c1");
    }

    @Test
    @DisplayName("actor 的 LIKE 通配符按字面处理，空白串忽略")
    void should_escapeAndIgnoreBlank_when_actorFiltering() {
        repository.save(AuditEvent.create(AuditEventType.LLM_CALL, "c1", "admin", "t",
                "d", true, 10, T1));

        AuditEventPage wildcard = repository.query(new AuditEventQuery(
                null, null, "%", null, null, null, 0, 10));
        AuditEventPage blank = repository.query(new AuditEventQuery(
                null, null, "  ", null, null, null, 0, 10));

        assertThat(wildcard.total()).isZero();
        assertThat(blank.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("findById 命中返回事件，未命中返回空")
    void should_findById_when_exists() {
        AuditEvent saved = repository.save(newEvent(AuditEventType.TOOL_CALL, "conv-9", T1));

        assertThat(repository.findById(saved.id())).isPresent()
                .get()
                .extracting(AuditEvent::traceId, AuditEvent::eventType)
                .containsExactly("conv-9", AuditEventType.TOOL_CALL);
        assertThat(repository.findById(99999L)).isEmpty();
    }
}
