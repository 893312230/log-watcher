package com.smartops.common.enums;

/**
 * 任务状态枚举。
 *
 * <p>描述一个运维任务从创建到完成的生命周期状态。
 * 状态流转：CREATED → RUNNING → (SUCCESS | FAILED | CANCELLED)。</p>
 *
 * <p>线程安全：枚举天然不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public enum TaskStatus {

    /**
     * 已创建：任务已接收但尚未开始执行。
     */
    CREATED,

    /**
     * 运行中：Agent 正在执行该任务。
     */
    RUNNING,

    /**
     * 已成功：任务执行完成且结果符合预期。
     */
    SUCCESS,

    /**
     * 已失败：任务执行过程中发生错误或结果不符合预期。
     */
    FAILED,

    /**
     * 已取消：由用户或系统主动取消执行。
     */
    CANCELLED,
}
