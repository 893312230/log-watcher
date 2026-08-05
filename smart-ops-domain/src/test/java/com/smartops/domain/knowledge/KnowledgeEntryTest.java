package com.smartops.domain.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeEntryTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    @Test
    @DisplayName("create 构建完整条目")
    void should_createEntry() {
        KnowledgeEntry e = KnowledgeEntry.create("OOM 错误", "OutOfMemoryError",
                "堆内存不足", "增大 -Xmx", List.of("重启服务", "修改 JVM 参数"),
                "JVM", List.of("memory", "oom"), "MANUAL", null, null, "admin", NOW);
        assertThat(e.title()).isEqualTo("OOM 错误");
        assertThat(e.actionItems()).hasSize(2);
        assertThat(e.source()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("空标题抛异常")
    void should_throw_when_titleBlank() {
        assertThatThrownBy(() -> KnowledgeEntry.create("", null, null, null, null, null, null, null, null, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("source 缺省为 MANUAL")
    void should_defaultToManual_when_sourceNull() {
        KnowledgeEntry e = KnowledgeEntry.create("test", null, null, null, null, null, null, null, null, null, null, NOW);
        assertThat(e.source()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("withId 返回带 id 副本")
    void should_returnCopyWithId() {
        KnowledgeEntry e = KnowledgeEntry.create("t", null, null, null, null, null, null, null, null, null, null, NOW);
        assertThat(e.withId(1L).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("标题超长抛异常")
    void should_throw_when_titleTooLong() {
        String longTitle = "x".repeat(KnowledgeEntry.TITLE_MAX_LENGTH + 1);
        assertThatThrownBy(() -> KnowledgeEntry.create(longTitle, null, null, null, null, null, null, null, null, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("source 为空串时默认 MANUAL")
    void should_defaultSource_when_blank() {
        KnowledgeEntry e = KnowledgeEntry.create("t", null, null, null, null, null, null, "  ", null, null, null, NOW);
        assertThat(e.source()).isEqualTo("MANUAL");
    }
}

class KnowledgeEntryQueryTest {
    @Test
    @DisplayName("page 为负时归零")
    void should_clampPageToZero() {
        KnowledgeEntryQuery q = new KnowledgeEntryQuery(null, null, null, -1, 20);
        assertThat(q.page()).isZero();
    }

    @Test
    @DisplayName("size 超出范围时归为默认 20")
    void should_defaultSize_when_outOfRange() {
        KnowledgeEntryQuery q = new KnowledgeEntryQuery(null, null, null, 0, 200);
        assertThat(q.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("size 小于 1 时归为默认 20")
    void should_defaultSize_when_belowOne() {
        KnowledgeEntryQuery q = new KnowledgeEntryQuery(null, null, null, 0, 0);
        assertThat(q.size()).isEqualTo(20);
    }
}

/**
 * {@link KnowledgeEntryPage} 单元测试。
 */
class KnowledgeEntryPageTest {

    @Test
    @DisplayName("items 为 null 时归一化为空列表")
    void should_defaultItems_when_null() {
        KnowledgeEntryPage p = new KnowledgeEntryPage(null, 0, 0, 20);
        assertThat(p.items()).isEmpty();
        assertThat(p.total()).isZero();
    }

    @Test
    @DisplayName("items 防御性复制且内容保留")
    void should_copyItems_when_provided() {
        KnowledgeEntry e = KnowledgeEntry.create("t", null, "c", null,
                java.util.List.of(), "INFO", java.util.List.of(), "MANUAL",
                null, null, "tester", java.time.Instant.now());
        KnowledgeEntryPage p = new KnowledgeEntryPage(java.util.List.of(e), 1, 0, 20);
        assertThat(p.items()).containsExactly(e);
        assertThat(p.total()).isEqualTo(1);
    }
}
