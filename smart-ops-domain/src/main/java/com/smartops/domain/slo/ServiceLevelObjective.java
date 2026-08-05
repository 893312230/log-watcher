package com.smartops.domain.slo;

import java.util.Objects;

/**
 * 服务等级目标（阶段八 SLO 管理）。
 */
public record ServiceLevelObjective(
        Long id, String name, String serviceName, String metricName,
        double targetPercent, int windowDays, double errorBudgetPercent,
        boolean enabled
) {
    public ServiceLevelObjective {
        Objects.requireNonNull(name);
        if (targetPercent <= 0 || targetPercent > 100) targetPercent = 99.9;
        if (windowDays < 1) windowDays = 30;
    }
}
