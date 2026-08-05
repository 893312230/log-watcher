package com.smartops.infrastructure.persistence.runbook.impl;

import com.smartops.domain.runbook.Runbook;
import com.smartops.infrastructure.persistence.runbook.RunbookJpaRepository;
import com.smartops.infrastructure.persistence.runbook.RunbookStepJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RunbookRepositoryImpl} 数据层集成测试（@DataJpaTest + H2）。
 *
 * @author smartops
 * @since 1.0.0
 */
@DataJpaTest
class RunbookRepositoryImplTest {

    @Autowired
    private RunbookJpaRepository runbookJpa;

    @Autowired
    private RunbookStepJpaRepository stepJpa;

    private RunbookRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new RunbookRepositoryImpl(runbookJpa, stepJpa);
    }

    private Runbook newRunbook(String name, List<String> steps) {
        return new Runbook(null, name, "desc", "OOM", steps, 3, "rollback text", true);
    }

    @Test
    @DisplayName("保存后分配 id，步骤按序号往返且类型正确推导")
    void should_roundTripWithSteps_when_saved() {
        Runbook saved = repository.save(newRunbook("rb1",
                List.of("HTTP GET https://example.com", "WEBHOOK https://example.com/hook", "检查磁盘空间")));

        assertThat(saved.id()).isNotNull();
        Optional<Runbook> found = repository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("rb1");
        assertThat(found.get().steps()).containsExactly(
                "HTTP GET https://example.com", "WEBHOOK https://example.com/hook", "检查磁盘空间");
        assertThat(found.get().safetyLevel()).isEqualTo(3);
        assertThat(found.get().rollbackSteps()).isEqualTo("rollback text");
        assertThat(stepJpa.findByRunbookIdOrderBySeqAsc(saved.id()))
                .extracting("stepType")
                .containsExactly("HTTP", "WEBHOOK", "LLM");
    }

    @Test
    @DisplayName("重复保存同一 id 时步骤整体替换")
    void should_replaceSteps_when_resaved() {
        Runbook saved = repository.save(newRunbook("rb1", List.of("步骤A", "步骤B")));

        repository.save(new Runbook(saved.id(), "rb1", "desc", "OOM",
                List.of("新步骤"), 3, null, true));

        assertThat(repository.findById(saved.id())).isPresent();
        assertThat(repository.findById(saved.id()).get().steps()).containsExactly("新步骤");
    }

    @Test
    @DisplayName("findAll 返回全部 Runbook 含步骤")
    void should_findAll() {
        repository.save(newRunbook("rb1", List.of("s1")));
        repository.save(newRunbook("rb2", List.of("s2", "s3")));

        List<Runbook> all = repository.findAll();

        assertThat(all).hasSize(2);
        assertThat(all).allSatisfy(rb -> assertThat(rb.steps()).isNotEmpty());
    }

    @Test
    @DisplayName("deleteById 级联删除步骤")
    void should_deleteSteps_when_deleted() {
        Runbook saved = repository.save(newRunbook("rb1", List.of("s1")));

        repository.deleteById(saved.id());

        assertThat(repository.findById(saved.id())).isEmpty();
        assertThat(stepJpa.findByRunbookIdOrderBySeqAsc(saved.id())).isEmpty();
    }
}
