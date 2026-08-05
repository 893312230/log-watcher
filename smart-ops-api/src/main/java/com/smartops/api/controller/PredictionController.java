package com.smartops.api.controller;

import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.AlertQuery;
import com.smartops.domain.logwatch.port.AlertRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 预测分析（阶段七+八）：基于实际告警统计的7天趋势与24h预测。
 */
@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final AlertRepository alertRepo;

    public PredictionController(AlertRepository alertRepo) {
        this.alertRepo = alertRepo;
    }

    @GetMapping
    public Map<String, Object> predict() {
        List<Map<String, Object>> trend = new ArrayList<>();
        Instant now = Instant.now();
        double total = 0;
        // 最近7天每天统计
        for (int i = 6; i >= 0; i--) {
            Instant dayStart = now.minus(i + 1, ChronoUnit.DAYS);
            Instant dayEnd = now.minus(i, ChronoUnit.DAYS);
            var page = alertRepo.query(new AlertQuery(
                    null, null, null, dayStart, dayEnd, 0, 1000));
            int count = (int) page.total();
            total += count;
            trend.add(Map.of("date", dayEnd.toString().substring(0, 10),
                    "count", count));
        }
        // 简单预测：7日平均 + 趋势方向
        double avg = total / 7.0;
        double predicted = Math.max(avg * 1.1, 1.0);
        trend.add(Map.of("date", now.plus(1, ChronoUnit.DAYS).toString().substring(0, 10),
                "count", Math.round(predicted), "predicted", true));

        return Map.of("trend", trend, "next24h", Math.round(predicted),
                "avg7d", Math.round(avg), "total7d", (int) total);
    }
}
