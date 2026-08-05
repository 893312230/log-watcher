package com.smartops.domain.logwatch;

import com.smartops.common.enums.AlertLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ClassificationResult} 单元测试。
 */
class ClassificationResultTest {

    @Test
    @DisplayName("abstain 弃权结果为 INFO + 零置信")
    void should_return_info_with_zero_confidence_when_abstain() {
        ClassificationResult result = ClassificationResult.abstain();

        assertThat(result.level()).isEqualTo(AlertLevel.INFO);
        assertThat(result.confidence()).isZero();
    }

    @Test
    @DisplayName("record 访问器返回构造值")
    void should_expose_constructor_values() {
        ClassificationResult result = new ClassificationResult(AlertLevel.ERROR, 0.92);

        assertThat(result.level()).isEqualTo(AlertLevel.ERROR);
        assertThat(result.confidence()).isEqualTo(0.92);
    }

    @Test
    @DisplayName("相等性按值比较")
    void should_compare_by_value() {
        assertThat(new ClassificationResult(AlertLevel.WARN, 0.5))
                .isEqualTo(new ClassificationResult(AlertLevel.WARN, 0.5))
                .hasSameHashCodeAs(new ClassificationResult(AlertLevel.WARN, 0.5));
    }
}
