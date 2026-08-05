package com.smartops.api.controller;

import com.smartops.agent.logwatch.anomaly.StatisticalBaselineDetector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final StatisticalBaselineDetector detector;

    public AnomalyController(ObjectProvider<StatisticalBaselineDetector> provider) {
        this.detector = provider.getIfAvailable();
    }

    @GetMapping
    public Map<String, Object> getBaselines() {
        if (detector == null) return Collections.singletonMap("active", false);
        return Map.of("active", true, "baselines", detector.snapshots());
    }
}
