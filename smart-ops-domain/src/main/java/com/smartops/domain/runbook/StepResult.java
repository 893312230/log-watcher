package com.smartops.domain.runbook;

/**
 * Runbook 单步执行结果。
 *
 * @param seq     步骤序号（从 1 开始）
 * @param command 步骤原始指令文本
 * @param status  执行状态（SUCCESS / FAILED / SKIPPED）
 * @param output  执行输出或错误信息
 */
public record StepResult(int seq, String command, String status, String output) {
}
