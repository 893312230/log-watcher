package com.smartops.domain.logwatch;

import com.smartops.common.enums.AlertLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AnalysisContext} 单元测试。
 *
 * <p>验证分析上下文在管线各层间的累积语义：
 * 层级只增不减、知识引用只增不改、发生次数可累加。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AnalysisContextTest {

    private static final LogEvent EVENT =
            new LogEvent("app.log", "ERROR boom", Instant.parse("2026-07-22T10:00:00Z"));

    @Test
    @DisplayName("构造后事件与指纹只读可取")
    void should_exposeEventAndFingerprint_when_constructed() {
        AnalysisContext ctx = new AnalysisContext(EVENT);

        assertThat(ctx.getEvent()).isSameAs(EVENT);
        assertThat(ctx.getFingerprint()).isEqualTo(EVENT.fingerprint());
        assertThat(ctx.getOccurrence()).isEqualTo(1);
        assertThat(ctx.getLayerReached()).isZero();
        assertThat(ctx.isEscalate()).isFalse();
    }

    @Test
    @DisplayName("事件为 null 时构造失败")
    void should_throw_when_eventNull() {
        assertThatThrownBy(() -> new AnalysisContext(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("markLayerReached 只记录到达过的最高层")
    void should_keepMaxLayer_when_markLayerReachedCalled() {
        AnalysisContext ctx = new AnalysisContext(EVENT);

        ctx.markLayerReached(2);
        ctx.markLayerReached(1);
        ctx.markLayerReached(3);

        assertThat(ctx.getLayerReached()).isEqualTo(3);
    }

    @Test
    @DisplayName("知识引用逐条累积")
    void should_accumulateRefs_when_addKnowledgeRefCalled() {
        AnalysisContext ctx = new AnalysisContext(EVENT);

        ctx.addKnowledgeRef("runbooks/db-timeout.md");
        ctx.addKnowledgeRef("runbooks/restart-order.md");

        assertThat(ctx.getKnowledgeRefs())
                .containsExactly("runbooks/db-timeout.md", "runbooks/restart-order.md");
    }

    @Test
    @DisplayName("发生次数可累加（L0 合并同类告警）")
    void should_accumulateOccurrence_when_incremented() {
        AnalysisContext ctx = new AnalysisContext(EVENT);

        ctx.incrementOccurrence(4);

        assertThat(ctx.getOccurrence()).isEqualTo(5);
    }

    @Test
    @DisplayName("各层产出字段可写入并读取")
    void should_writeAndReadLayerOutputs_when_settersCalled() {
        AnalysisContext ctx = new AnalysisContext(EVENT);

        ctx.setLevel(AlertLevel.ERROR);
        ctx.setMatchedKeyword("OutOfMemoryError");
        ctx.setAnalysis("堆内存不足");
        ctx.setSuggestion("调大 -Xmx 并排查内存泄漏");
        ctx.markEscalate();

        assertThat(ctx.getLevel()).isEqualTo(AlertLevel.ERROR);
        assertThat(ctx.getMatchedKeyword()).isEqualTo("OutOfMemoryError");
        assertThat(ctx.getAnalysis()).isEqualTo("堆内存不足");
        assertThat(ctx.getSuggestion()).isEqualTo("调大 -Xmx 并排查内存泄漏");
        assertThat(ctx.isEscalate()).isTrue();
    }
}
