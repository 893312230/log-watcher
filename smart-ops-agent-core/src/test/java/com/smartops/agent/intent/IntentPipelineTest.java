package com.smartops.agent.intent;

import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IntentPipeline} 单元测试。
 *
 * <p>验证三层意图识别 Pipeline 的执行流程：
 * L1 具体规则短路（阈值 0.85）→ L2 一致性短路 → L4 LLM 兜底 → 冲突解决。
 * 原 L3 伪 ML 分类器已移除（ADR-011），各层识别器与冲突解决器均 Mock。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class IntentPipelineTest {

    private L1RegexRecognizer l1;
    private L2KeywordRecognizer l2;
    private L4LLMRecognizer l4;
    private ConflictResolver conflictResolver;
    private IntentPipeline pipeline;

    @BeforeEach
    void setUp() {
        l1 = mock(L1RegexRecognizer.class);
        l2 = mock(L2KeywordRecognizer.class);
        l4 = mock(L4LLMRecognizer.class);
        conflictResolver = mock(ConflictResolver.class);
        pipeline = new IntentPipeline(l1, l2, l4, conflictResolver);
    }

    @Nested
    @DisplayName("L1 短路（阈值 0.85）")
    class L1ShortCircuit {

        @Test
        @DisplayName("L1 具体规则命中（0.9 ≥ 0.85）时短路返回，不调用 L2/L4")
        void should_shortCircuit_when_l1ConcreteHits() {
            IntentResult l1Result = new IntentResult(IntentType.EXECUTE_OPERATION, 0.9, "L1_REGEX", null);
            when(l1.recognize(anyString())).thenReturn(l1Result);

            IntentResult result = pipeline.recognize("重启订单服务");

            assertThat(result).isSameAs(l1Result);
            verify(l2, never()).recognize(anyString());
            verify(l4, never()).recognize(anyString());
            verify(conflictResolver, never()).resolve(anyList());
        }

        @Test
        @DisplayName("L1 置信度恰好等于阈值 0.85 时短路返回")
        void should_shortCircuit_when_l1AtThreshold() {
            IntentResult l1Result = new IntentResult(IntentType.EXECUTE_OPERATION, 0.85, "L1_REGEX", null);
            when(l1.recognize(anyString())).thenReturn(l1Result);

            IntentResult result = pipeline.recognize("重启订单服务");

            assertThat(result).isSameAs(l1Result);
            verify(l2, never()).recognize(anyString());
        }

        @Test
        @DisplayName("L1 宽泛兜底命中（0.4 < 0.85）时不短路，继续调用 L2")
        void should_notShortCircuit_when_l1BroadFallback() {
            IntentResult l1Result = new IntentResult(IntentType.QUERY_METRIC, 0.4, "L1_REGEX", null);
            IntentResult l2Result = new IntentResult(IntentType.QUERY_METRIC, 0.75, "L2_KEYWORD", null);
            when(l1.recognize(anyString())).thenReturn(l1Result);
            when(l2.recognize(anyString())).thenReturn(l2Result);

            pipeline.recognize("查询 CPU 使用率");

            verify(l2).recognize("查询 CPU 使用率");
        }
    }

    @Nested
    @DisplayName("L2 一致性短路")
    class L2ShortCircuit {

        @Test
        @DisplayName("L2 与 L1 一致且高置信度时短路返回 L2 结果，不调用 L4")
        void should_shortCircuit_when_l2AgreesWithL1() {
            IntentResult l1Result = new IntentResult(IntentType.QUERY_METRIC, 0.4, "L1_REGEX", null);
            IntentResult l2Result = new IntentResult(IntentType.QUERY_METRIC, 0.75, "L2_KEYWORD", null);
            when(l1.recognize(anyString())).thenReturn(l1Result);
            when(l2.recognize(anyString())).thenReturn(l2Result);

            IntentResult result = pipeline.recognize("查询 CPU 使用率");

            assertThat(result).isSameAs(l2Result);
            verify(l4, never()).recognize(anyString());
            verify(conflictResolver, never()).resolve(anyList());
        }

        @Test
        @DisplayName("L2 高置信度但与 L1 不一致时不短路，继续调用 L4")
        void should_notShortCircuit_when_l2DisagreesWithL1() {
            IntentResult l1Result = new IntentResult(IntentType.QUERY_METRIC, 0.4, "L1_REGEX", null);
            IntentResult l2Result = new IntentResult(IntentType.KNOWLEDGE_QA, 0.75, "L2_KEYWORD", null);
            IntentResult l4Result = new IntentResult(IntentType.KNOWLEDGE_QA, 0.9, "L4_LLM", null);
            when(l1.recognize(anyString())).thenReturn(l1Result);
            when(l2.recognize(anyString())).thenReturn(l2Result);
            when(l4.recognize(anyString())).thenReturn(l4Result);

            IntentResult result = pipeline.recognize("CPU 如何使用");

            assertThat(result).isSameAs(l4Result);
            verify(l4).recognize("CPU 如何使用");
        }
    }

    @Nested
    @DisplayName("L4 兜底")
    class L4Fallback {

        @Test
        @DisplayName("L4 高置信度时返回 L4 结果，不经冲突解决器")
        void should_returnL4_when_l4Confident() {
            IntentResult l1Result = new IntentResult(IntentType.UNKNOWN, 0.2, "L1_REGEX", null);
            IntentResult l2Result = new IntentResult(IntentType.UNKNOWN, 0.15, "L2_KEYWORD", null);
            IntentResult l4Result = new IntentResult(IntentType.ROOT_CAUSE, 0.9, "L4_LLM", null);
            when(l1.recognize(anyString())).thenReturn(l1Result);
            when(l2.recognize(anyString())).thenReturn(l2Result);
            when(l4.recognize(anyString())).thenReturn(l4Result);

            IntentResult result = pipeline.recognize("服务为何频繁超时");

            assertThat(result).isSameAs(l4Result);
            verify(conflictResolver, never()).resolve(anyList());
        }
    }

    @Nested
    @DisplayName("冲突解决")
    class ConflictResolution {

        @Test
        @DisplayName("三层结果冲突且 L4 置信度不足时由冲突解决器加权投票")
        void should_useConflictResolver_when_allLowConfidence() {
            IntentResult l1Result = new IntentResult(IntentType.QUERY_METRIC, 0.4, "L1_REGEX", null);
            IntentResult l2Result = new IntentResult(IntentType.KNOWLEDGE_QA, 0.5, "L2_KEYWORD", null);
            IntentResult l4Result = new IntentResult(IntentType.ROOT_CAUSE, 0.5, "L4_LLM", null);
            IntentResult resolvedResult = new IntentResult(IntentType.QUERY_METRIC, 0.6, "VOTED:L1_REGEX", null);
            when(l1.recognize(anyString())).thenReturn(l1Result);
            when(l2.recognize(anyString())).thenReturn(l2Result);
            when(l4.recognize(anyString())).thenReturn(l4Result);
            when(conflictResolver.resolve(anyList())).thenReturn(resolvedResult);

            IntentResult result = pipeline.recognize("CPU 为什么高");

            assertThat(result).isSameAs(resolvedResult);
            verify(conflictResolver).resolve(anyList());
        }

        @Test
        @DisplayName("传递给冲突解决器的结果包含 L1/L2/L4 三层（无 L3）")
        void should_passThreeLayerResults_when_conflictOccurs() {
            IntentResult l1Result = new IntentResult(IntentType.QUERY_METRIC, 0.4, "L1_REGEX", null);
            IntentResult l2Result = new IntentResult(IntentType.KNOWLEDGE_QA, 0.5, "L2_KEYWORD", null);
            IntentResult l4Result = new IntentResult(IntentType.ROOT_CAUSE, 0.5, "L4_LLM", null);
            when(l1.recognize(anyString())).thenReturn(l1Result);
            when(l2.recognize(anyString())).thenReturn(l2Result);
            when(l4.recognize(anyString())).thenReturn(l4Result);
            when(conflictResolver.resolve(anyList()))
                    .thenReturn(new IntentResult(IntentType.QUERY_METRIC, 0.6, "VOTED", null));

            pipeline.recognize("CPU 为什么高");

            org.mockito.ArgumentCaptor<java.util.List<IntentResult>> captor =
                    org.mockito.ArgumentCaptor.forClass(java.util.List.class);
            verify(conflictResolver).resolve(captor.capture());
            assertThat(captor.getValue())
                    .containsExactly(l1Result, l2Result, l4Result);
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("输入为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputNull() {
            assertThatThrownBy(() -> pipeline.recognize(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("输入为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputBlank() {
            assertThatThrownBy(() -> pipeline.recognize("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("识别器管理")
    class RecognizerManagement {

        @Test
        @DisplayName("getRecognizers 返回三层识别器（L1/L2/L4，无 L3）")
        void should_returnThreeRecognizers_when_getRecognizers() {
            assertThat(pipeline.getRecognizers())
                    .containsExactly(l1, l2, l4)
                    .hasSize(3);
        }
    }
}
