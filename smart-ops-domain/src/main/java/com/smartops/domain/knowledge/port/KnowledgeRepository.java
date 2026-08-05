package com.smartops.domain.knowledge.port;

import com.smartops.domain.knowledge.KnowledgeEntry;
import com.smartops.domain.knowledge.KnowledgeEntryPage;
import com.smartops.domain.knowledge.KnowledgeEntryQuery;

import java.util.List;
import java.util.Optional;

/**
 * 知识库持久化端口。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface KnowledgeRepository {

    KnowledgeEntryPage query(KnowledgeEntryQuery query);

    Optional<KnowledgeEntry> findById(long id);

    KnowledgeEntry save(KnowledgeEntry entry);

    void deleteById(long id);

    /**
     * 去重查询非空分类（供筛选下拉），按字典序升序。
     *
     * @return 分类列表（不含 null/空串）
     */
    List<String> listCategories();
}
