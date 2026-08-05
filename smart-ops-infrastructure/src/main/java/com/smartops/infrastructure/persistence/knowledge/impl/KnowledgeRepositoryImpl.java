package com.smartops.infrastructure.persistence.knowledge.impl;

import com.smartops.domain.knowledge.KnowledgeEntry;
import com.smartops.domain.knowledge.KnowledgeEntryPage;
import com.smartops.domain.knowledge.KnowledgeEntryQuery;
import com.smartops.domain.knowledge.port.KnowledgeRepository;
import com.smartops.infrastructure.persistence.knowledge.KnowledgeEntryEntity;
import com.smartops.infrastructure.persistence.knowledge.KnowledgeEntryJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeRepositoryImpl implements KnowledgeRepository {

    private final KnowledgeEntryJpaRepository jpa;

    public KnowledgeRepositoryImpl(KnowledgeEntryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public KnowledgeEntryPage query(KnowledgeEntryQuery q) {
        Specification<KnowledgeEntryEntity> spec = (root, query, cb) -> {
            var preds = new ArrayList<Predicate>();
            if (q.keyword() != null && !q.keyword().isBlank()) {
                String kw = "%" + q.keyword() + "%";
                preds.add(cb.or(
                        cb.like(root.get("title"), kw),
                        cb.like(root.get("rootCause"), kw),
                        cb.like(root.get("suggestion"), kw)));
            }
            if (q.category() != null && !q.category().isBlank()) {
                preds.add(cb.equal(root.get("category"), q.category()));
            }
            if (q.source() != null && !q.source().isBlank()) {
                preds.add(cb.equal(root.get("source"), q.source()));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
        Page<KnowledgeEntryEntity> page = jpa.findAll(spec,
                PageRequest.of(q.page(), q.size(), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new KnowledgeEntryPage(
                page.getContent().stream().map(this::toDomain).toList(),
                page.getTotalElements(), q.page(), q.size());
    }

    @Override
    public Optional<KnowledgeEntry> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public KnowledgeEntry save(KnowledgeEntry entry) {
        return toDomain(jpa.save(toEntity(entry)));
    }

    @Override
    public void deleteById(long id) {
        jpa.deleteById(id);
    }

    @Override
    public List<String> listCategories() {
        return jpa.findDistinctCategories();
    }

    private KnowledgeEntry toDomain(KnowledgeEntryEntity e) {
        return new KnowledgeEntry(e.getId(), e.getTitle(), e.getErrorPattern(),
                e.getRootCause(), e.getSuggestion(),
                splitList(e.getActionItems()), e.getCategory(),
                splitList(e.getTags()), e.getSource(),
                e.getSourceAlertId(), e.getServerConfigId(), e.getCreatedBy(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private KnowledgeEntryEntity toEntity(KnowledgeEntry e) {
        KnowledgeEntryEntity entity = new KnowledgeEntryEntity();
        entity.setId(e.id());
        entity.setTitle(e.title());
        entity.setErrorPattern(e.errorPattern());
        entity.setRootCause(e.rootCause());
        entity.setSuggestion(e.suggestion());
        entity.setActionItems(e.actionItems() != null
                ? String.join(",", e.actionItems()) : null);
        entity.setCategory(e.category());
        entity.setTags(e.tags() != null ? String.join(",", e.tags()) : null);
        entity.setSource(e.source());
        entity.setSourceAlertId(e.sourceAlertId());
        entity.setServerConfigId(e.serverConfigId());
        entity.setCreatedBy(e.createdBy());
        entity.setCreatedAt(e.createdAt());
        entity.setUpdatedAt(e.updatedAt());
        return entity;
    }

    private static List<String> splitList(String s) {
        return s != null && !s.isBlank()
                ? Arrays.asList(s.split(",")) : Collections.emptyList();
    }
}
