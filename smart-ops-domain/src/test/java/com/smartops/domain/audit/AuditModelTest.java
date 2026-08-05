package com.smartops.domain.audit;

import com.smartops.common.enums.AuditEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * {@link AuditEvent}、{@link AuditEventQuery}、{@link AuditEventPage} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class AuditModelTest {

    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");

    @Test
    @DisplayName("create 创建未落库事件，字段完整")
    void should_createEvent_when_validInput() {
        AuditEvent event = AuditEvent.create(AuditEventType.LLM_CALL, "conv-1",
                "chatService", "kimi-for-coding", "调用成功", true, 123, NOW);

        assertThat(event.id()).isNull();
        assertThat(event.eventType()).isEqualTo(AuditEventType.LLM_CALL);
        assertThat(event.traceId()).isEqualTo("conv-1");
        assertThat(event.actor()).isEqualTo("chatService");
        assertThat(event.target()).isEqualTo("kimi-for-coding");
        assertThat(event.detail()).isEqualTo("调用成功");
        assertThat(event.success()).isTrue();
        assertThat(event.latencyMs()).isEqualTo(123);
        assertThat(event.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("detail 超长自动截断到上限")
    void should_truncateDetail_when_tooLong() {
        String longDetail = "x".repeat(AuditEvent.DETAIL_MAX_LENGTH + 100);

        AuditEvent event = AuditEvent.create(AuditEventType.TOOL_CALL, null,
                "queryMetric", null, longDetail, true, 1, NOW);

        assertThat(event.detail()).hasSize(AuditEvent.DETAIL_MAX_LENGTH);
    }

    @Test
    @DisplayName("负耗时归一化为 0")
    void should_clampLatency_when_negative() {
        AuditEvent event = AuditEvent.create(AuditEventType.TASK_EXECUTION, null,
                "agentRouter", null, null, false, -5, NOW);

        assertThat(event.latencyMs()).isZero();
    }

    @Test
    @DisplayName("必填字段为 null 时抛出 NPE")
    void should_throwNPE_when_requiredFieldNull() {
        assertThatNullPointerException().isThrownBy(() ->
                AuditEvent.create(null, null, "actor", null, null, true, 0, NOW));
        assertThatNullPointerException().isThrownBy(() ->
                AuditEvent.create(AuditEventType.LLM_CALL, null, null, null, null, true, 0, NOW));
        assertThatNullPointerException().isThrownBy(() ->
                AuditEvent.create(AuditEventType.LLM_CALL, null, "actor", null, null, true, 0, null));
    }

    @Test
    @DisplayName("withId 返回带 id 副本，其余字段不变")
    void should_copyWithId_when_persisted() {
        AuditEvent event = AuditEvent.create(AuditEventType.SECURITY_DECISION, "t",
                "securityGate", "restart", "denied", false, 0, NOW);

        AuditEvent persisted = event.withId(42L);

        assertThat(persisted.id()).isEqualTo(42L);
        assertThat(persisted.eventType()).isEqualTo(AuditEventType.SECURITY_DECISION);
        assertThat(persisted.actor()).isEqualTo("securityGate");
    }

    @Test
    @DisplayName("查询分页参数归一化")
    void should_normalizePaging_when_queryCreated() {
        AuditEventQuery query = new AuditEventQuery(null, null, null, null, null, null, -1, 9999);

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(AuditEventQuery.MAX_PAGE_SIZE);
        assertThat(new AuditEventQuery(null, null, null, null, null, null, 0, 0).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("分页结果防御性拷贝且拒绝 null 结果集")
    void should_defensiveCopy_when_pageCreated() {
        AuditEvent event = AuditEvent.create(AuditEventType.LLM_CALL, null,
                "actor", null, null, true, 0, NOW);
        AuditEventPage page = new AuditEventPage(List.of(event), 1, 0, 10);

        assertThat(page.items()).containsExactly(event);
        assertThat(page.total()).isEqualTo(1);
        assertThatNullPointerException().isThrownBy(() -> new AuditEventPage(null, 0, 0, 10));
    }
}
