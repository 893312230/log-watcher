package com.smartops.api.controller;

import com.smartops.infrastructure.persistence.slo.SloEntity;
import com.smartops.infrastructure.persistence.slo.SloJpaRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * SLO 管理 REST 入口（持久化存储）。
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/slos")
public class SloController {

    private final SloJpaRepository repository;

    /**
     * 构造 SLO 控制器。
     *
     * @param repository SLO 仓库
     */
    public SloController(SloJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页查询 SLO。
     *
     * @param page 页码（0 起始，可选）
     * @param size 每页大小（默认 100，上限 500，可选）
     * @return SLO 列表
     */
    @GetMapping
    public List<SloEntity> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return com.smartops.api.support.PageSlice.slice(repository.findAll(), page, size);
    }

    /**
     * 创建 SLO。
     *
     * @param body 请求体（name/serviceName/metricName/targetPercent/windowDays/errorBudgetPercent/enabled）
     * @return 含分配 id 的 SLO
     */
    @PostMapping
    public SloEntity create(@RequestBody Map<String, Object> body) {
        SloEntity entity = new SloEntity();
        entity.setName((String) body.get("name"));
        entity.setServiceName((String) body.get("serviceName"));
        entity.setMetricName((String) body.get("metricName"));
        entity.setTargetPct(body.get("targetPercent") instanceof Number n ? n.doubleValue() : 99.9);
        entity.setWindowDays(body.get("windowDays") instanceof Number n ? n.intValue() : 30);
        entity.setErrorBudgetPct(body.get("errorBudgetPercent") instanceof Number n ? n.doubleValue() : 0.1);
        entity.setEnabled(body.getOrDefault("enabled", true) instanceof Boolean b ? b : true);
        return repository.save(entity);
    }

    /**
     * 查询指定 SLO 的当前状态（达成率与错误预算为占位计算，待接入真实指标）。
     *
     * @param id SLO id
     * @return SLO 及状态
     */
    @GetMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable long id) {
        SloEntity slo = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SLO 不存在: " + id));
        return Map.of("slo", slo, "currentPercent", 99.7, "errorBudgetRemaining", 85.0);
    }

    /**
     * 删除指定 SLO。
     *
     * @param id SLO id
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        repository.deleteById(id);
    }
}
