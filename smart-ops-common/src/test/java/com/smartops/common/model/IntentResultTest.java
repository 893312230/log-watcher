package com.smartops.common.model;

import com.smartops.common.enums.IntentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link IntentResult} 单元测试。
 *
 * <p>验证意图识别结果的构造、校验、不可变性等契约。
 * 对应 agent.md 阶段二意图识别体系。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class IntentResultTest {

    @Nested
    @DisplayName("构造与校验")
    class Construction {

        @Test
        @DisplayName("合法参数构造成功")
        void should_construct_when_validParams() {
            IntentResult result = new IntentResult(
                    IntentType.QUERY_METRIC, 0.95,
                    IntentResult.SOURCE_L1_REGEX,
                    Map.of("metricName", "cpu_usage")
            );

            assertThat(result.intentType()).isEqualTo(IntentType.QUERY_METRIC);
            assertThat(result.confidence()).isEqualTo(0.95);
            assertThat(result.source()).isEqualTo("L1_REGEX");
            assertThat(result.extractedEntities()).containsEntry("metricName", "cpu_usage");
        }

        @Test
        @DisplayName("实体 Map 为 null 时返回空 Map")
        void should_returnEmptyMap_when_entitiesNull() {
            IntentResult result = new IntentResult(
                    IntentType.UNKNOWN, 0.0, "NONE", null
            );

            assertThat(result.extractedEntities()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("意图类型为 null 时抛出 NullPointerException")
        void should_throwNpe_when_intentTypeIsNull() {
            assertThatThrownBy(() -> new IntentResult(null, 0.5, "L1", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("意图类型");
        }

        @Test
        @DisplayName("来源为 null 时抛出 NullPointerException")
        void should_throwNpe_when_sourceIsNull() {
            assertThatThrownBy(() -> new IntentResult(IntentType.UNKNOWN, 0.5, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("识别来源");
        }

        @Test
        @DisplayName("置信度小于 0 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_confidenceNegative() {
            assertThatThrownBy(() -> new IntentResult(IntentType.UNKNOWN, -0.1, "L1", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0.0-1.0");
        }

        @Test
        @DisplayName("置信度大于 1 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_confidenceExceedsOne() {
            assertThatThrownBy(() -> new IntentResult(IntentType.UNKNOWN, 1.1, "L1", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("置信度为边界值 0.0 和 1.0 时构造成功")
        void should_construct_when_confidenceAtBoundaries() {
            assertThat(new IntentResult(IntentType.UNKNOWN, 0.0, "L1", null)).isNotNull();
            assertThat(new IntentResult(IntentType.UNKNOWN, 1.0, "L1", null)).isNotNull();
        }
    }

    @Nested
    @DisplayName("不可变性")
    class Immutability {

        @Test
        @DisplayName("返回的实体 Map 不可修改")
        void should_returnUnmodifiableMap_when_entitiesAccessed() {
            IntentResult result = new IntentResult(
                    IntentType.QUERY_METRIC, 0.9, "L1", Map.of("key", "value")
            );

            assertThatThrownBy(() -> result.extractedEntities().put("newKey", "newValue"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("原始 Map 修改不影响 IntentResult")
        void should_notBeAffected_when_originalMapModified() {
            Map<String, String> mutable = new java.util.HashMap<>(Map.of("key", "value"));
            IntentResult result = new IntentResult(IntentType.QUERY_METRIC, 0.9, "L1", mutable);

            mutable.put("newKey", "newValue");

            assertThat(result.extractedEntities()).hasSize(1).containsOnlyKeys("key");
        }
    }

    @Nested
    @DisplayName("isConfident 方法")
    class IsConfident {

        @Test
        @DisplayName("置信度等于阈值 0.6 时返回 true")
        void should_returnTrue_when_confidenceEqualsThreshold() {
            IntentResult result = new IntentResult(IntentType.QUERY_METRIC, 0.6, "L1", null);

            assertThat(result.isConfident()).isTrue();
        }

        @Test
        @DisplayName("置信度高于阈值时返回 true")
        void should_returnTrue_when_confidenceAboveThreshold() {
            IntentResult result = new IntentResult(IntentType.QUERY_METRIC, 0.8, "L1", null);

            assertThat(result.isConfident()).isTrue();
        }

        @Test
        @DisplayName("置信度低于阈值时返回 false")
        void should_returnFalse_when_confidenceBelowThreshold() {
            IntentResult result = new IntentResult(IntentType.QUERY_METRIC, 0.59, "L1", null);

            assertThat(result.isConfident()).isFalse();
        }
    }

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("unknown 方法返回 UNKNOWN 类型、0 置信度")
        void should_returnUnknownResult_when_unknownCalled() {
            IntentResult result = IntentResult.unknown();

            assertThat(result.intentType()).isEqualTo(IntentType.UNKNOWN);
            assertThat(result.confidence()).isEqualTo(0.0);
            assertThat(result.extractedEntities()).isEmpty();
        }

        @Test
        @DisplayName("of 方法创建不含实体的简化结果")
        void should_createWithoutEntities_when_ofCalled() {
            IntentResult result = IntentResult.of(IntentType.ROOT_CAUSE, 0.75, "L4_LLM");

            assertThat(result.intentType()).isEqualTo(IntentType.ROOT_CAUSE);
            assertThat(result.confidence()).isEqualTo(0.75);
            assertThat(result.source()).isEqualTo("L4_LLM");
            assertThat(result.extractedEntities()).isEmpty();
        }
    }

    @Test
    @DisplayName("来源常量值正确")
    void should_haveCorrectConstants_when_accessed() {
        assertThat(IntentResult.SOURCE_L1_REGEX).isEqualTo("L1_REGEX");
        assertThat(IntentResult.SOURCE_L2_KEYWORD).isEqualTo("L2_KEYWORD");
        assertThat(IntentResult.SOURCE_L4_LLM).isEqualTo("L4_LLM");
        assertThat(IntentResult.CONFIDENCE_THRESHOLD).isEqualTo(0.6);
    }
}
