package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.domain.knowledge.KnowledgeChunk;
import com.smartops.domain.knowledge.KnowledgeRetriever;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import com.smartops.domain.logwatch.LogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link L2RagLayer} 单元测试。
 *
 * <p>覆盖：无检索器降级跳过、有命中累积参考、无命中放行、检索异常放行。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class L2RagLayerTest {

    private AnalysisContext ctxOf(String content) {
        return new AnalysisContext(new LogEvent("app.log", content, Instant.now()));
    }

    @Test
    @DisplayName("层级 order 为 3")
    void should_orderTwo_when_orderCalled() {
        assertThat(new L2RagLayer(null).order()).isEqualTo(3);
    }

    @Test
    @DisplayName("知识库未接入（检索器为 null）时直接放行，不产生参考")
    void should_proceedWithoutRefs_when_retrieverAbsent() {
        L2RagLayer layer = new L2RagLayer(null);
        AnalysisContext ctx = ctxOf("ERROR db timeout");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.getKnowledgeRefs()).isEmpty();
        assertThat(ctx.getLayerReached()).isEqualTo(3);
    }

    @Test
    @DisplayName("检索命中时累积知识参考并放行")
    void should_accumulateRefs_when_hitsFound() {
        KnowledgeRetriever retriever = (query, topK) -> List.of(
                new KnowledgeChunk("1", "数据库超时排查步骤", "runbooks/db-timeout.md", "DB 超时", 0.9),
                new KnowledgeChunk("2", "连接池配置", "runbooks/pool.md", "连接池", 0.7));
        L2RagLayer layer = new L2RagLayer(retriever);
        AnalysisContext ctx = ctxOf("ERROR db timeout");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.getKnowledgeRefs())
                .containsExactly("runbooks/db-timeout.md", "runbooks/pool.md");
        assertThat(ctx.getLayerReached()).isEqualTo(3);
    }

    @Test
    @DisplayName("检索无命中时放行且无参考")
    void should_proceedWithoutRefs_when_noHits() {
        L2RagLayer layer = new L2RagLayer((query, topK) -> List.of());
        AnalysisContext ctx = ctxOf("ERROR weird thing");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.getKnowledgeRefs()).isEmpty();
    }

    @Test
    @DisplayName("超长日志内容截断为 500 字符检索查询")
    void should_truncateQuery_when_contentTooLong() {
        StringBuilder captured = new StringBuilder();
        KnowledgeRetriever retriever = (query, topK) -> {
            captured.append(query);
            return List.of();
        };
        L2RagLayer layer = new L2RagLayer(retriever);
        AnalysisContext ctx = ctxOf("E".repeat(800));

        layer.apply(ctx);

        assertThat(captured).hasSize(500);
    }

    @Test
    @DisplayName("检索器异常时降级放行，不中断管线")
    void should_proceed_when_retrieverThrows() {
        KnowledgeRetriever broken = (query, topK) -> {
            throw new RuntimeException("ES down");
        };
        L2RagLayer layer = new L2RagLayer(broken);
        AnalysisContext ctx = ctxOf("ERROR db timeout");

        AnalysisOutcome outcome = layer.apply(ctx);

        assertThat(outcome.verdict()).isEqualTo(AnalysisOutcome.Verdict.PROCEED);
        assertThat(ctx.getKnowledgeRefs()).isEmpty();
    }
}
