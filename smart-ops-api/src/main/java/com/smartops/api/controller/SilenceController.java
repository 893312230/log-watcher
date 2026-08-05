package com.smartops.api.controller;

import com.smartops.infrastructure.persistence.silence.SilenceWindowEntity;
import com.smartops.infrastructure.persistence.silence.SilenceWindowJpaRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 告警静默窗口管理 REST 入口（持久化存储）。
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/silences")
public class SilenceController {

    private final SilenceWindowJpaRepository repository;

    /**
     * 构造静默窗口控制器。
     *
     * @param repository 静默窗口仓库
     */
    public SilenceController(SilenceWindowJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页查询静默窗口（按开始时间倒序）。
     *
     * @param page 页码（0 起始，可选）
     * @param size 每页大小（默认 100，上限 500，可选）
     * @return 静默窗口列表
     */
    @GetMapping
    public List<SilenceWindowEntity> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return com.smartops.api.support.PageSlice.slice(repository.findAll(), page, size);
    }

    /**
     * 创建静默窗口。
     *
     * @param body 请求体（sourceMatcher/levelFilter/startAt/endAt/reason/createdBy）
     * @return 含分配 id 的静默窗口
     */
    @PostMapping
    public SilenceWindowEntity create(@RequestBody Map<String, Object> body) {
        SilenceWindowEntity entity = new SilenceWindowEntity();
        entity.setSourceMatcher((String) body.get("sourceMatcher"));
        entity.setLevelFilter((String) body.get("levelFilter"));
        entity.setStartAt(parseInstant(body.get("startAt"), Instant.now()));
        entity.setEndAt(parseInstant(body.get("endAt"), Instant.now().plusSeconds(3600)));
        entity.setReason((String) body.get("reason"));
        entity.setCreatedBy((String) body.getOrDefault("createdBy",
                com.smartops.api.auth.CurrentActor.username()));
        entity.setCreatedAt(Instant.now());
        return repository.save(entity);
    }

    /**
     * 删除指定静默窗口。
     *
     * @param id 静默窗口 id
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        repository.deleteById(id);
    }

    private static Instant parseInstant(Object value, Instant fallback) {
        if (value instanceof String s && !s.isBlank()) {
            return Instant.parse(s);
        }
        return fallback;
    }
}
