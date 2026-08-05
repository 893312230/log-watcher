package com.smartops.api.controller;

import com.smartops.infrastructure.persistence.notification.NotificationChannelEntity;
import com.smartops.infrastructure.persistence.notification.NotificationChannelJpaRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 通知渠道管理 REST 入口（持久化存储）。
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationChannelJpaRepository repository;

    /**
     * 构造通知渠道控制器。
     *
     * @param repository 通知渠道仓库
     */
    public NotificationController(NotificationChannelJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页查询通知渠道。
     *
     * @param page 页码（0 起始，可选）
     * @param size 每页大小（默认 100，上限 500，可选）
     * @return 渠道列表
     */
    @GetMapping
    public List<NotificationChannelEntity> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return com.smartops.api.support.PageSlice.slice(repository.findAll(), page, size);
    }

    /**
     * 创建通知渠道。
     *
     * @param body 请求体（name/type/url/enabled）
     * @return 含分配 id 的渠道
     */
    @PostMapping
    public NotificationChannelEntity add(@RequestBody Map<String, Object> body) {
        NotificationChannelEntity entity = new NotificationChannelEntity();
        entity.setName((String) body.get("name"));
        entity.setType((String) body.getOrDefault("type", "WEBHOOK"));
        entity.setTargetUrl((String) body.get("url"));
        entity.setEnabled(body.getOrDefault("enabled", true) instanceof Boolean b ? b : true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return repository.save(entity);
    }

    /**
     * 删除指定通知渠道。
     *
     * @param id 渠道 id
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        repository.deleteById(id);
    }
}
