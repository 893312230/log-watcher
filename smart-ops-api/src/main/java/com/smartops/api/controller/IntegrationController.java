package com.smartops.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartops.infrastructure.persistence.integration.IntegrationEntity;
import com.smartops.infrastructure.persistence.integration.IntegrationJpaRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 第三方集成适配器（阶段七）：Jira/GitHub/GitLab Webhook 配置（持久化存储）。
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    private final IntegrationJpaRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造集成控制器。
     *
     * @param repository 集成配置仓库
     */
    public IntegrationController(IntegrationJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页查询集成配置。
     *
     * @param page 页码（0 起始，可选）
     * @param size 每页大小（默认 100，上限 500，可选）
     * @return 集成列表
     */
    @GetMapping
    public List<IntegrationEntity> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return com.smartops.api.support.PageSlice.slice(repository.findAll(), page, size);
    }

    /**
     * 创建集成配置（config 任意 JSON 对象，落库为文本）。
     *
     * @param body 请求体（type/name/config/enabled）
     * @return 含分配 id 的集成配置
     */
    @PostMapping
    public IntegrationEntity add(@RequestBody Map<String, Object> body) throws Exception {
        IntegrationEntity entity = new IntegrationEntity();
        entity.setType((String) body.getOrDefault("type", "WEBHOOK"));
        entity.setName((String) body.get("name"));
        Object config = body.get("config");
        entity.setConfigJson(config == null ? null : objectMapper.writeValueAsString(config));
        entity.setEnabled(body.getOrDefault("enabled", true) instanceof Boolean b ? b : true);
        return repository.save(entity);
    }

    /**
     * 删除指定集成配置。
     *
     * @param id 集成 id
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        repository.deleteById(id);
    }
}
