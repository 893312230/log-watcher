package com.smartops.infrastructure.persistence.knowledge.impl;

import com.smartops.domain.knowledge.KnowledgeEntry;
import com.smartops.domain.knowledge.KnowledgeEntryPage;
import com.smartops.domain.knowledge.KnowledgeEntryQuery;
import com.smartops.infrastructure.persistence.knowledge.KnowledgeEntryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class KnowledgeRepositoryImplTest {

    @Autowired
    private KnowledgeEntryJpaRepository jpa;

    private KnowledgeRepositoryImpl repo;

    @BeforeEach
    void setUp() {
        repo = new KnowledgeRepositoryImpl(jpa);
    }

    private KnowledgeEntry entry(String title, String category, String source) {
        return KnowledgeEntry.create(title, null, "根因", "建议", List.of(),
                category, null, source, null, null, "test", Instant.now());
    }

    @Test
    @DisplayName("save 后可查询")
    void should_findAfterSave() {
        repo.save(entry("OOM 错误", "JVM", "MANUAL"));
        KnowledgeEntryPage page = repo.query(new KnowledgeEntryQuery(null, null, null, 0, 20));
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("按分类过滤")
    void should_filterByCategory() {
        repo.save(entry("a", "JVM", "MANUAL"));
        repo.save(entry("b", "NETWORK", "MANUAL"));
        KnowledgeEntryPage page = repo.query(new KnowledgeEntryQuery(null, "JVM", null, 0, 20));
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("关键字搜索匹配标题")
    void should_searchByKeyword() {
        repo.save(entry("OutOfMemoryError 排查", "JVM", "LOGWATCH"));
        repo.save(entry("网络超时", "NETWORK", "MANUAL"));
        KnowledgeEntryPage page = repo.query(new KnowledgeEntryQuery("OutOfMemory", null, null, 0, 20));
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteById 后不可查")
    void should_notFindAfterDelete() {
        KnowledgeEntry saved = repo.save(entry("x", null, "MANUAL"));
        repo.deleteById(saved.id());
        assertThat(repo.findById(saved.id())).isEmpty();
    }

    @Test
    @DisplayName("listCategories 去重且排除空分类")
    void should_listDistinctCategories() {
        repo.save(entry("a", "JVM", "MANUAL"));
        repo.save(entry("b", "NETWORK", "MANUAL"));
        repo.save(entry("c", "JVM", "LOGWATCH"));
        repo.save(entry("d", null, "MANUAL"));
        repo.save(entry("e", "", "MANUAL"));

        assertThat(repo.listCategories()).containsExactly("JVM", "NETWORK");
    }
}
