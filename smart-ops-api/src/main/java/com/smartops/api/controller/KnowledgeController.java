package com.smartops.api.controller;

import com.smartops.api.dto.KnowledgeEntryPageView;
import com.smartops.api.dto.KnowledgeEntryView;
import com.smartops.domain.knowledge.KnowledgeEntry;
import com.smartops.domain.knowledge.KnowledgeEntryQuery;
import com.smartops.domain.knowledge.port.KnowledgeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 运维知识库 Controller（阶段六知识库）。
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final int DEFAULT_SIZE = 20;

    private final KnowledgeRepository repository;

    public KnowledgeController(KnowledgeRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public KnowledgeEntryPageView list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        KnowledgeEntryQuery query = new KnowledgeEntryQuery(keyword, category, source, page, size);
        return KnowledgeEntryPageView.from(repository.query(query));
    }

    @GetMapping("/{id}")
    public KnowledgeEntryView get(@PathVariable long id) {
        return repository.findById(id)
                .map(KnowledgeEntryView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public KnowledgeEntryView create(@RequestBody Map<String, Object> body) {
        KnowledgeEntry entry = KnowledgeEntry.create(
                (String) body.get("title"),
                (String) body.get("errorPattern"),
                (String) body.get("rootCause"),
                (String) body.get("suggestion"),
                parseList(body.get("actionItems")),
                (String) body.get("category"),
                parseList(body.get("tags")),
                (String) body.getOrDefault("source", "MANUAL"),
                toLong(body.get("sourceAlertId")),
                toLong(body.get("serverConfigId")),
                (String) body.getOrDefault("createdBy", ""),
                Instant.now());
        return KnowledgeEntryView.from(repository.save(entry));
    }

    @PutMapping("/{id}")
    public KnowledgeEntryView update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        KnowledgeEntry existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        KnowledgeEntry updated = new KnowledgeEntry(id,
                (String) body.getOrDefault("title", existing.title()),
                (String) body.getOrDefault("errorPattern", existing.errorPattern()),
                (String) body.getOrDefault("rootCause", existing.rootCause()),
                (String) body.getOrDefault("suggestion", existing.suggestion()),
                body.containsKey("actionItems") ? parseList(body.get("actionItems")) : existing.actionItems(),
                (String) body.getOrDefault("category", existing.category()),
                body.containsKey("tags") ? parseList(body.get("tags")) : existing.tags(),
                existing.source(),
                existing.sourceAlertId(),
                body.containsKey("serverConfigId") ? toLong(body.get("serverConfigId")) : existing.serverConfigId(),
                existing.createdBy(),
                existing.createdAt(),
                Instant.now());
        return KnowledgeEntryView.from(repository.save(updated));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        if (repository.findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }

    @PostMapping("/search")
    public KnowledgeEntryPageView search(@RequestBody Map<String, Object> body) {
        String keyword = (String) body.getOrDefault("keyword", "");
        String category = (String) body.get("category");
        String source = (String) body.get("source");
        int page = body.containsKey("page") ? ((Number) body.get("page")).intValue() : 0;
        int size = body.containsKey("size") ? ((Number) body.get("size")).intValue() : DEFAULT_SIZE;
        return KnowledgeEntryPageView.from(
                repository.query(new KnowledgeEntryQuery(keyword, category, source, page, size)));
    }

    /**
     * 去重查询非空分类（供前端筛选下拉）。
     *
     * @return 分类列表（升序，不含空串）
     */
    @GetMapping("/categories")
    public List<String> categories() {
        return repository.listCategories();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseList(Object obj) {
        if (obj instanceof List) return (List<String>) obj;
        if (obj instanceof String s && !s.isBlank()) return List.of(s.split(","));
        return List.of();
    }

    private Long toLong(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        if (obj instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
