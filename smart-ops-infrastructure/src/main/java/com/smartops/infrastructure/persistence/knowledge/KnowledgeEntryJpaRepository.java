package com.smartops.infrastructure.persistence.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KnowledgeEntryJpaRepository extends
        JpaRepository<KnowledgeEntryEntity, Long>,
        JpaSpecificationExecutor<KnowledgeEntryEntity> {

    /** 去重查询非空分类（供筛选下拉），按字典序升序。 */
    @Query("select distinct k.category from KnowledgeEntryEntity k "
            + "where k.category is not null and k.category <> '' order by k.category")
    List<String> findDistinctCategories();
}
