package com.smartops.domain.runbook;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runbook 定义（阶段七自动修复引擎）。
 *
 * <p>预定义的操作手册：触发条件匹配告警后按步骤执行，
 * 含预检查、安全等级和回滚方案。</p>
 */
public record Runbook(
        Long id, String name, String description, String triggerKeyword,
        List<String> steps, int safetyLevel, String rollbackSteps,
        boolean enabled
) {
    public Runbook {
        Objects.requireNonNull(name);
        steps = steps == null ? Collections.emptyList() : List.copyOf(steps);
        if (safetyLevel < 1) safetyLevel = 1;
    }
}
