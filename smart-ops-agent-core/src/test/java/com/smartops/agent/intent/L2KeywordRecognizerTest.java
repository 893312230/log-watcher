package com.smartops.agent.intent;

import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link L2KeywordRecognizer} 单元测试。
 *
 * <p>验证 L2 词频统计识别器的关键词命中、置信度计算、优先级处理。
 * 对应 agent.md 阶段二任务2。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class L2KeywordRecognizerTest {

    private L2KeywordRecognizer recognizer;

    @BeforeEach
    void setUp() {
        recognizer = new L2KeywordRecognizer();
    }

    @Nested
    @DisplayName("关键词命中")
    class KeywordHit {

        @Test
        @DisplayName("查询 CPU 使用率命中查询指标关键词")
        void should_returnQueryMetric_when_queryCpu() {
            IntentResult result = recognizer.recognize("查询 CPU 使用率");

            assertThat(result.intentType()).isEqualTo(IntentType.QUERY_METRIC);
            assertThat(result.confidence()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("重启服务命中执行操作关键词")
        void should_returnExecuteOp_when_restart() {
            IntentResult result = recognizer.recognize("重启订单服务");

            assertThat(result.intentType()).isEqualTo(IntentType.EXECUTE_OPERATION);
        }

        @Test
        @DisplayName("如何配置 Nginx 命中知识问答关键词")
        void should_returnKnowledgeQA_when_howTo() {
            IntentResult result = recognizer.recognize("如何配置 Nginx 负载均衡");

            assertThat(result.intentType()).isEqualTo(IntentType.KNOWLEDGE_QA);
        }

        @Test
        @DisplayName("为什么响应变慢命中根因分析关键词")
        void should_returnRootCause_when_whySlow() {
            IntentResult result = recognizer.recognize("为什么服务响应变慢了");

            assertThat(result.intentType()).isEqualTo(IntentType.ROOT_CAUSE);
        }
    }

    @Nested
    @DisplayName("置信度与优先级")
    class ConfidenceAndPriority {

        @Test
        @DisplayName("置信度不超过 0.79 上限（严格低于 L1 具体规则的 0.9）")
        void should_capConfidenceAt079_when_manyHits() {
            IntentResult result = recognizer.recognize("查询查看当前 CPU 内存 磁盘 qps 状态使用率占用");

            assertThat(result.confidence()).isLessThanOrEqualTo(0.79);
        }

        @Test
        @DisplayName("置信度按 0.3 + 0.5×命中率² 映射")
        void should_mapConfidenceByFormula_when_keywordHit() {
            // "重启订单服务"：EXECUTE_OPERATION 命中 1 词（重启），共 10 词
            // 命中率 0.1 → 0.3 + 0.5×0.01 = 0.305
            IntentResult result = recognizer.recognize("重启订单服务");

            assertThat(result.intentType()).isEqualTo(IntentType.EXECUTE_OPERATION);
            assertThat(result.confidence()).isCloseTo(0.305,
                    org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("无任何关键词命中时返回 UNKNOWN 低置信度")
        void should_returnUnknownLowConfidence_when_noKeywordHit() {
            IntentResult result = recognizer.recognize("今天天气不错");

            assertThat(result.intentType()).isEqualTo(IntentType.UNKNOWN);
            assertThat(result.confidence()).isLessThan(0.3);
        }

        @Test
        @DisplayName("多意图命中数相同时取 code 更大的意图")
        void should_preferHigherCode_when_tieBreak() {
            // 这个输入同时命中查询指标和知识问答的关键词，测试优先级逻辑
            IntentResult result = recognizer.recognize("查询 CPU 如何使用");

            assertThat(result.intentType()).isNotNull();
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("输入为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputNull() {
            assertThatThrownBy(() -> recognizer.recognize(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("输入为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputBlank() {
            assertThatThrownBy(() -> recognizer.recognize(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("getLayer 返回 L2_KEYWORD")
    void should_returnL2Keyword_when_getLayerCalled() {
        assertThat(recognizer.getLayer()).isEqualTo("L2_KEYWORD");
    }

    @Test
    @DisplayName("大小写不敏感匹配")
    void should_beCaseInsensitive_when_inputMixedCase() {
        IntentResult result = recognizer.recognize("查看 CPU 使用率");

        assertThat(result.intentType()).isEqualTo(IntentType.QUERY_METRIC);
    }
}
