package com.smartops.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Runbook 创建请求 DTO。
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param name           名称（必填）
 * @param description    描述
 * @param triggerKeyword 触发关键字
 * @param steps          执行步骤指令列表
 * @param safetyLevel    安全等级（≥4 需审批，默认 1）
 * @param rollbackSteps  失败回滚步骤（每行一条）
 * @param enabled        是否启用（默认 true）
 */
public record RunbookRequest(
        @NotBlank(message = "名称不能为空") String name,
        String description,
        String triggerKeyword,
        List<String> steps,
        Integer safetyLevel,
        String rollbackSteps,
        Boolean enabled
) {
}
