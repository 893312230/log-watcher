package com.smartops.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartops.infrastructure.persistence.oncall.OnCallRotationEntity;
import com.smartops.infrastructure.persistence.oncall.OnCallRotationJpaRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 值班调度 REST 入口（持久化存储，单条默认轮换）。
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/oncall")
public class OnCallController {

    private static final String DEFAULT_ROTATION = "default";

    private final OnCallRotationJpaRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造值班控制器。
     *
     * @param repository 值班轮换仓库
     */
    public OnCallController(OnCallRotationJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询今日值班人及整周排班。
     *
     * @return 今日星期、值班人、整周排班
     */
    @GetMapping("/current")
    public Map<String, Object> current() throws Exception {
        Map<String, String> rotation = loadRotation();
        String day = java.time.DayOfWeek.from(java.time.LocalDate.now()).name().substring(0, 3);
        return Map.of("day", day, "person", rotation.getOrDefault(day, "未知"),
                "rotation", rotation);
    }

    /**
     * 更新排班（合并到现有排班）。
     *
     * @param body 星期缩写 → 值班人
     * @return 更新后的整周排班
     */
    @PostMapping("/rotation")
    public Map<String, String> update(@RequestBody Map<String, String> body) throws Exception {
        OnCallRotationEntity entity = loadOrCreate();
        Map<String, String> rotation = parseMembers(entity.getMembersJson());
        rotation.putAll(body);
        entity.setMembersJson(objectMapper.writeValueAsString(rotation));
        repository.save(entity);
        return rotation;
    }

    private Map<String, String> loadRotation() throws Exception {
        return parseMembers(loadOrCreate().getMembersJson());
    }

    private OnCallRotationEntity loadOrCreate() throws Exception {
        return repository.findByName(DEFAULT_ROTATION).orElseGet(() -> {
            OnCallRotationEntity entity = new OnCallRotationEntity();
            entity.setName(DEFAULT_ROTATION);
            entity.setHandoffDay("MONDAY");
            entity.setCurrentIndex(0);
            Map<String, String> seed = new LinkedHashMap<>();
            seed.put("MON", "值班员A"); seed.put("TUE", "值班员B");
            seed.put("WED", "值班员A"); seed.put("THU", "值班员B");
            seed.put("FRI", "值班员A"); seed.put("SAT", "值班员C");
            seed.put("SUN", "值班员C");
            try {
                entity.setMembersJson(objectMapper.writeValueAsString(seed));
            } catch (Exception e) {
                throw new IllegalStateException("排班序列化失败", e);
            }
            return repository.save(entity);
        });
    }

    private Map<String, String> parseMembers(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
    }
}
