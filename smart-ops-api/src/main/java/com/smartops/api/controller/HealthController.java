package com.smartops.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private final DataSource dataSource;
    private final RedisConnectionFactory redisFactory;

    public HealthController(ObjectProvider<DataSource> dsProvider,
                            ObjectProvider<RedisConnectionFactory> redisProvider) {
        this.dataSource = dsProvider.getIfAvailable();
        this.redisFactory = redisProvider.getIfAvailable();
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean dbUp = checkDb();
        boolean redisUp = checkRedis();
        result.put("status", (dbUp && redisUp) ? "UP" : "DOWN");
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("application", "smart-ops-agent");
        result.put("db", dbUp ? "UP" : "DOWN");
        result.put("redis", redisUp ? "UP" : "DOWN");
        if (!dbUp || !redisUp) {
            log.warn("健康检查失败: db={}, redis={}", dbUp ? "UP" : "DOWN",
                    redisUp ? "UP" : "DOWN");
        }
        return result;
    }

    private boolean checkDb() {
        if (dataSource == null) return true; // 无 DB 依赖时跳过
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2);
        } catch (Exception e) { return false; }
    }

    private boolean checkRedis() {
        if (redisFactory == null) return true;
        try {
            var conn = redisFactory.getConnection();
            conn.ping();
            conn.close();
            return true;
        } catch (Exception e) { return false; }
    }
}
