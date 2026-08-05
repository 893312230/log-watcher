package com.smartops.api.controller;

import com.smartops.api.dto.ServerConfigView;
import com.smartops.domain.config.ServerConfig;
import com.smartops.domain.config.port.ServerConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 服务器配置管理 Controller（阶段六配置管理）。
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/servers")
public class ServerConfigController {

    private final ServerConfigRepository repository;

    public ServerConfigController(ServerConfigRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ServerConfigView> list() {
        return repository.findAll().stream().map(ServerConfigView::from).toList();
    }

    @GetMapping("/{id}")
    public ServerConfigView get(@PathVariable long id) {
        return repository.findById(id)
                .map(ServerConfigView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ServerConfigView create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        ServerConfig config = ServerConfig.create(name,
                (String) body.get("host"),
                (String) body.get("deployPath"),
                (String) body.get("codeRepo"),
                (String) body.get("logPath"),
                (String) body.get("description"),
                parseTags(body.get("tags")),
                Instant.now());
        return ServerConfigView.from(repository.save(config));
    }

    @PutMapping("/{id}")
    public ServerConfigView update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        ServerConfig existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ServerConfig updated = new ServerConfig(id,
                (String) body.getOrDefault("name", existing.name()),
                (String) body.getOrDefault("host", existing.host()),
                (String) body.getOrDefault("deployPath", existing.deployPath()),
                (String) body.getOrDefault("codeRepo", existing.codeRepo()),
                (String) body.getOrDefault("logPath", existing.logPath()),
                (String) body.getOrDefault("description", existing.description()),
                body.containsKey("tags") ? parseTags(body.get("tags")) : existing.tags(),
                existing.createdAt(),
                Instant.now());
        return ServerConfigView.from(repository.save(updated));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        if (repository.findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTags(Object tagsObj) {
        if (tagsObj instanceof List) {
            return (List<String>) tagsObj;
        }
        if (tagsObj instanceof String s && !s.isBlank()) {
            return List.of(s.split(","));
        }
        return List.of();
    }
}
