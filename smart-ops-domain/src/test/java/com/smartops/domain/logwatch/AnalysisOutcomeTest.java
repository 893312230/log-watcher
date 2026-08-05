package com.smartops.domain.logwatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AnalysisOutcome} 单元测试。
 *
 * <p>验证层产出三种裁决的工厂方法与取值。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AnalysisOutcomeTest {

    @Test
    @DisplayName("suppress 工厂产出 SUPPRESS 裁决")
    void should_returnSuppressVerdict_when_suppressCalled() {
        assertThat(AnalysisOutcome.suppress().verdict())
                .isEqualTo(AnalysisOutcome.Verdict.SUPPRESS);
    }

    @Test
    @DisplayName("proceed 工厂产出 PROCEED 裁决")
    void should_returnProceedVerdict_when_proceedCalled() {
        assertThat(AnalysisOutcome.proceed().verdict())
                .isEqualTo(AnalysisOutcome.Verdict.PROCEED);
    }

    @Test
    @DisplayName("complete 工厂产出 COMPLETE 裁决")
    void should_returnCompleteVerdict_when_completeCalled() {
        assertThat(AnalysisOutcome.complete().verdict())
                .isEqualTo(AnalysisOutcome.Verdict.COMPLETE);
    }

    @Test
    @DisplayName("裁决为 null 时构造失败")
    void should_throw_when_verdictNull() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AnalysisOutcome(null))
                .isInstanceOf(NullPointerException.class);
    }
}
