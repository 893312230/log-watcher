package com.smartops.agent.intent;

import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ConflictResolver} 单元测试。
 *
 * <p>验证加权投票冲突解决器的分组累加、权重计算、UNKNOWN 过滤、边界条件。
 * 对应 agent.md 阶段二任务5。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ConflictResolverTest {

    private ConflictResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ConflictResolver();
    }

    @Nested
    @DisplayName("加权投票")
    class WeightedVoting {

        @Test
        @DisplayName("多个识别器投同一意图时累加加权分数")
        void should_aggregateScore_when_multipleVotersSameIntent() {
            IntentResult l1 = new IntentResult(IntentType.QUERY_METRIC, 0.9, IntentResult.SOURCE_L1_REGEX, null);
            IntentResult l2 = new IntentResult(IntentType.QUERY_METRIC, 0.7, IntentResult.SOURCE_L2_KEYWORD, null);

            IntentResult resolved = resolver.resolve(List.of(l1, l2));

            assertThat(resolved.intentType()).isEqualTo(IntentType.QUERY_METRIC);
            // 归一化加权平均 = (0.9*1.0 + 0.7*0.7) / (1.0 + 0.7) = 1.39 / 1.7 ≈ 0.818
            assertThat(resolved.confidence()).isBetween(0.81, 0.83);
            assertThat(resolved.source()).startsWith("VOTED:");
            assertThat(resolved.source()).contains("L1_REGEX");
            assertThat(resolved.source()).contains("L2_KEYWORD");
        }

        @Test
        @DisplayName("两个识别器投不同意图时选分数最高的")
        void should_pickHigherScore_when_differentIntents() {
            // L1 投 QUERY_METRIC（权重 1.0），L4 投 ROOT_CAUSE（权重 0.9）
            IntentResult l1 = new IntentResult(IntentType.QUERY_METRIC, 0.5, IntentResult.SOURCE_L1_REGEX, null);
            IntentResult l4 = new IntentResult(IntentType.ROOT_CAUSE, 0.9, IntentResult.SOURCE_L4_LLM, null);

            IntentResult resolved = resolver.resolve(List.of(l1, l4));

            // L4 分数 = 0.9 * 0.9 = 0.81，L1 分数 = 0.5 * 1.0 = 0.5，L4 胜出
            assertThat(resolved.intentType()).isEqualTo(IntentType.ROOT_CAUSE);
        }

        @Test
        @DisplayName("L1 权重（1.0）高于 L4（0.9）：置信度接近时 L1 胜出")
        void should_pickL1_when_confidenceClose() {
            // L1 0.85×1.0 = 0.85，L4 0.9×0.9 = 0.81，L1 以权重优势胜出
            IntentResult l1 = new IntentResult(IntentType.EXECUTE_OPERATION, 0.85, IntentResult.SOURCE_L1_REGEX, null);
            IntentResult l4 = new IntentResult(IntentType.QUERY_METRIC, 0.9, IntentResult.SOURCE_L4_LLM, null);

            IntentResult resolved = resolver.resolve(List.of(l1, l4));

            assertThat(resolved.intentType()).isEqualTo(IntentType.EXECUTE_OPERATION);
        }

        @Test
        @DisplayName("单个识别器结果直接返回")
        void should_returnSingleResult_when_onlyOne() {
            IntentResult l1 = new IntentResult(IntentType.TREND_ANALYSIS, 0.8, IntentResult.SOURCE_L1_REGEX, null);

            IntentResult resolved = resolver.resolve(List.of(l1));

            assertThat(resolved.intentType()).isEqualTo(IntentType.TREND_ANALYSIS);
            // 单一结果：分数 = 0.8*1.0 = 0.8，权重 = 1.0，置信度 = 0.8/1.0 = 0.8
            assertThat(resolved.confidence()).isEqualTo(0.8);
        }
    }

    @Nested
    @DisplayName("UNKNOWN 过滤")
    class UnknownFiltering {

        @Test
        @DisplayName("所有识别器返回 UNKNOWN 时返回 unknown()")
        void should_returnUnknown_when_allUnknown() {
            IntentResult l1 = new IntentResult(IntentType.UNKNOWN, 0.2, IntentResult.SOURCE_L1_REGEX, null);
            IntentResult l2 = new IntentResult(IntentType.UNKNOWN, 0.15, IntentResult.SOURCE_L2_KEYWORD, null);

            IntentResult resolved = resolver.resolve(List.of(l1, l2));

            assertThat(resolved.intentType()).isEqualTo(IntentType.UNKNOWN);
            assertThat(resolved.confidence()).isEqualTo(0.0);
            assertThat(resolved.source()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("部分 UNKNOWN 结果被过滤，不影响有效意图的投票")
        void should_filterUnknown_when_partialUnknown() {
            IntentResult l1 = new IntentResult(IntentType.UNKNOWN, 0.2, IntentResult.SOURCE_L1_REGEX, null);
            IntentResult l2 = new IntentResult(IntentType.QUERY_METRIC, 0.7, IntentResult.SOURCE_L2_KEYWORD, null);
            IntentResult l4 = new IntentResult(IntentType.UNKNOWN, 0.25, IntentResult.SOURCE_L4_LLM, null);

            IntentResult resolved = resolver.resolve(List.of(l1, l2, l4));

            assertThat(resolved.intentType()).isEqualTo(IntentType.QUERY_METRIC);
        }
    }

    @Nested
    @DisplayName("边界与异常")
    class BoundaryAndException {

        @Test
        @DisplayName("结果列表为空时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_emptyList() {
            assertThatThrownBy(() -> resolver.resolve(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("结果列表为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_nullList() {
            assertThatThrownBy(() -> resolver.resolve(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("未知来源使用默认权重 0.1")
        void should_useDefaultWeight_when_unknownSource() {
            IntentResult custom = new IntentResult(IntentType.KNOWLEDGE_QA, 0.9, "CUSTOM_SOURCE", null);

            IntentResult resolved = resolver.resolve(List.of(custom));

            // 默认权重 0.1，分数 = 0.9 * 0.1 = 0.09，权重总和 = 0.1，置信度 = 0.09/0.1 = 0.9
            assertThat(resolved.intentType()).isEqualTo(IntentType.KNOWLEDGE_QA);
            assertThat(resolved.confidence()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("最终置信度不超过 1.0")
        void should_capConfidenceAtOne_when_exceeds() {
            // 构造高置信度结果使加权分数超过 1.0
            IntentResult l1 = new IntentResult(IntentType.QUERY_METRIC, 1.0, IntentResult.SOURCE_L1_REGEX, null);
            IntentResult l4 = new IntentResult(IntentType.QUERY_METRIC, 1.0, IntentResult.SOURCE_L4_LLM, null);

            IntentResult resolved = resolver.resolve(List.of(l1, l4));

            assertThat(resolved.confidence()).isLessThanOrEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("VOTED 来源记录所有投票该意图的识别器")
    void should_recordVotersInSource_when_resolved() {
        IntentResult l1 = new IntentResult(IntentType.ANALYZE_ALERT, 0.85, IntentResult.SOURCE_L1_REGEX, null);
        IntentResult l2 = new IntentResult(IntentType.ANALYZE_ALERT, 0.65, IntentResult.SOURCE_L2_KEYWORD, null);
        IntentResult l4 = new IntentResult(IntentType.QUERY_METRIC, 0.7, IntentResult.SOURCE_L4_LLM, null);

        IntentResult resolved = resolver.resolve(List.of(l1, l2, l4));

        assertThat(resolved.intentType()).isEqualTo(IntentType.ANALYZE_ALERT);
        assertThat(resolved.source()).startsWith("VOTED:");
        // 来源应包含 L1 和 L2（投票 ANALYZE_ALERT 的），不包含 L4
        assertThat(resolved.source()).contains("L1_REGEX", "L2_KEYWORD");
    }
}
