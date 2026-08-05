package com.smartops.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TaskStatus} 枚举测试。
 *
 * <p>验证任务状态枚举的完整性与 valueOf 契约。
 * 对应 agent.md 第六章 6.2 节覆盖率要求。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class TaskStatusTest {

    @Test
    @DisplayName("枚举包含全部 5 个状态")
    void should_containAllStatuses_when_valuesCalled() {
        TaskStatus[] statuses = TaskStatus.values();

        assertThat(statuses).hasSize(5);
        assertThat(statuses).containsExactly(
                TaskStatus.CREATED,
                TaskStatus.RUNNING,
                TaskStatus.SUCCESS,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED
        );
    }

    @Test
    @DisplayName("valueOf 按名称正确返回枚举常量")
    void should_returnCorrectStatus_when_valueOfCalled() {
        assertThat(TaskStatus.valueOf("CREATED")).isEqualTo(TaskStatus.CREATED);
        assertThat(TaskStatus.valueOf("RUNNING")).isEqualTo(TaskStatus.RUNNING);
        assertThat(TaskStatus.valueOf("SUCCESS")).isEqualTo(TaskStatus.SUCCESS);
        assertThat(TaskStatus.valueOf("FAILED")).isEqualTo(TaskStatus.FAILED);
        assertThat(TaskStatus.valueOf("CANCELLED")).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    @DisplayName("每个状态有有意义的名称")
    void should_haveMeaningfulName_when_toStringCalled() {
        assertThat(TaskStatus.CREATED.toString()).isEqualTo("CREATED");
        assertThat(TaskStatus.FAILED.toString()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("枚举 ordinal 按生命周期顺序递增")
    void should_haveAscendingOrdinal_when_inLifecycleOrder() {
        assertThat(TaskStatus.CREATED.ordinal()).isLessThan(TaskStatus.RUNNING.ordinal());
        assertThat(TaskStatus.RUNNING.ordinal()).isLessThan(TaskStatus.SUCCESS.ordinal());
        assertThat(TaskStatus.SUCCESS.ordinal()).isLessThan(TaskStatus.FAILED.ordinal());
        assertThat(TaskStatus.FAILED.ordinal()).isLessThan(TaskStatus.CANCELLED.ordinal());
    }
}
