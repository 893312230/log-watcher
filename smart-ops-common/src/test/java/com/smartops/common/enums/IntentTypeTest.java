package com.smartops.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IntentType} 枚举测试。
 *
 * <p>验证意图类型的完整性、编码映射、显示名等契约。
 * 对应 agent.md 阶段二意图识别体系。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class IntentTypeTest {

    @Test
    @DisplayName("枚举包含全部 7 种意图类型")
    void should_containAllTypes_when_valuesCalled() {
        IntentType[] types = IntentType.values();

        assertThat(types).hasSize(7);
        assertThat(types).contains(
                IntentType.QUERY_METRIC,
                IntentType.TREND_ANALYSIS,
                IntentType.ANALYZE_ALERT,
                IntentType.ROOT_CAUSE,
                IntentType.EXECUTE_OPERATION,
                IntentType.KNOWLEDGE_QA,
                IntentType.UNKNOWN
        );
    }

    @Test
    @DisplayName("每个意图类型有非空的中文名称")
    void should_haveNonBlankDisplayName_when_allTypes() {
        for (IntentType type : IntentType.values()) {
            assertThat(type.getDisplayName()).isNotBlank();
        }
    }

    @Test
    @DisplayName("每个意图类型有唯一的数字编码")
    void should_haveUniqueCode_when_allTypes() {
        java.util.Set<Integer> codes = new java.util.HashSet<>();
        for (IntentType type : IntentType.values()) {
            assertThat(codes.add(type.getCode())).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    @DisplayName("fromCode 根据编码正确返回意图类型")
    void should_returnCorrectType_when_fromCodeCalled(int code) {
        IntentType type = IntentType.fromCode(code);

        assertThat(type.getCode()).isEqualTo(code);
        assertThat(type).isNotEqualTo(IntentType.UNKNOWN);
    }

    @Test
    @DisplayName("fromCode 传入无效编码返回 UNKNOWN")
    void should_returnUnknown_when_invalidCode() {
        assertThat(IntentType.fromCode(999)).isEqualTo(IntentType.UNKNOWN);
        assertThat(IntentType.fromCode(-1)).isEqualTo(IntentType.UNKNOWN);
        assertThat(IntentType.fromCode(0)).isEqualTo(IntentType.UNKNOWN);
    }

    @Test
    @DisplayName("UNKNOWN 的编码为 0")
    void should_haveZeroCode_when_unknownType() {
        assertThat(IntentType.UNKNOWN.getCode()).isEqualTo(0);
    }
}
