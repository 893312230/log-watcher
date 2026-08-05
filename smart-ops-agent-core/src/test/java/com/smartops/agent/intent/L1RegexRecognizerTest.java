package com.smartops.agent.intent;

import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link L1RegexRecognizer} 单元测试。
 *
 * <p>验证 L1 正则规则识别器的各场景匹配、实体提取、异常处理。
 * 对应 agent.md 阶段二任务1。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class L1RegexRecognizerTest {

    private L1RegexRecognizer recognizer;

    @BeforeEach
    void setUp() {
        recognizer = new L1RegexRecognizer();
    }

    @Nested
    @DisplayName("各意图场景匹配")
    class ScenarioMatching {

        @Test
        @DisplayName("查询 CPU 使用率识别为 QUERY_METRIC（宽泛兜底，置信度 0.4 不短路）")
        void should_returnQueryMetric_when_queryCpu() {
            IntentResult result = recognizer.recognize("查询 CPU 使用率");

            assertThat(result.intentType()).isEqualTo(IntentType.QUERY_METRIC);
            // 只命中宽泛兜底正则：0.4 < 通用阈值 0.6，不参与短路
            assertThat(result.confidence()).isEqualTo(0.4);
            assertThat(result.isConfident()).isFalse();
            assertThat(result.source()).isEqualTo("L1_REGEX");
        }

        @Test
        @DisplayName("具体规则命中时置信度为 0.9（可短路）")
        void should_return09Confidence_when_concreteRuleHits() {
            IntentResult result = recognizer.recognize("重启订单服务");

            assertThat(result.intentType()).isEqualTo(IntentType.EXECUTE_OPERATION);
            assertThat(result.confidence()).isEqualTo(0.9);
            assertThat(result.isConfident()).isTrue();
        }

        @Test
        @DisplayName("查看当前内存多少识别为 QUERY_METRIC")
        void should_returnQueryMetric_when_checkMemory() {
            IntentResult result = recognizer.recognize("查看当前内存多少");

            assertThat(result.intentType()).isEqualTo(IntentType.QUERY_METRIC);
        }

        @Test
        @DisplayName("重启服务识别为 EXECUTE_OPERATION")
        void should_returnExecuteOp_when_restartService() {
            IntentResult result = recognizer.recognize("重启订单服务");

            assertThat(result.intentType()).isEqualTo(IntentType.EXECUTE_OPERATION);
        }

        @Test
        @DisplayName("为什么服务响应变慢识别为 ROOT_CAUSE")
        void should_returnRootCause_when_whySlow() {
            IntentResult result = recognizer.recognize("为什么服务响应变慢了");

            assertThat(result.intentType()).isEqualTo(IntentType.ROOT_CAUSE);
        }

        @Test
        @DisplayName("分析最近一小时 CPU 趋势识别为 TREND_ANALYSIS")
        void should_returnTrendAnalysis_when_trendQuery() {
            IntentResult result = recognizer.recognize("分析最近一小时 CPU 趋势");

            assertThat(result.intentType()).isEqualTo(IntentType.TREND_ANALYSIS);
        }

        @Test
        @DisplayName("如何配置 Nginx 识别为 KNOWLEDGE_QA")
        void should_returnKnowledgeQA_when_howToConfig() {
            IntentResult result = recognizer.recognize("如何配置 Nginx 负载均衡");

            assertThat(result.intentType()).isEqualTo(IntentType.KNOWLEDGE_QA);
        }

        @Test
        @DisplayName("分析告警原因识别为 ANALYZE_ALERT")
        void should_returnAnalyzeAlert_when_alertAnalysis() {
            IntentResult result = recognizer.recognize("分析告警原因");

            assertThat(result.intentType()).isEqualTo(IntentType.ANALYZE_ALERT);
        }

        @Test
        @DisplayName("排查报警识别为 ANALYZE_ALERT（反向语序）")
        void should_returnAnalyzeAlert_when_reverseOrder() {
            IntentResult result = recognizer.recognize("排查报警问题");

            assertThat(result.intentType()).isEqualTo(IntentType.ANALYZE_ALERT);
        }
    }

    @Nested
    @DisplayName("实体提取")
    class EntityExtraction {

        @Test
        @DisplayName("查询 CPU 时提取 metricName=cpu_usage")
        void should_extractMetricName_when_queryCpu() {
            IntentResult result = recognizer.recognize("查询 CPU 使用率");

            assertThat(result.extractedEntities()).containsEntry("metricName", "cpu_usage");
        }

        @Test
        @DisplayName("查询内存时提取 metricName=memory_usage")
        void should_extractMetricName_when_queryMemory() {
            IntentResult result = recognizer.recognize("查看当前内存多少");

            assertThat(result.extractedEntities()).containsEntry("metricName", "memory_usage");
        }

        @Test
        @DisplayName("查询磁盘时提取 metricName=disk_usage")
        void should_extractMetricName_when_queryDisk() {
            IntentResult result = recognizer.recognize("查看磁盘使用率");

            assertThat(result.extractedEntities()).containsEntry("metricName", "disk_usage");
        }

        @Test
        @DisplayName("查询 QPS 时提取 metricName=qps")
        void should_extractMetricName_when_queryQps() {
            IntentResult result = recognizer.recognize("查看当前 QPS 多少");

            assertThat(result.extractedEntities()).containsEntry("metricName", "qps");
        }

        @Test
        @DisplayName("查询延迟时提取 metricName=latency")
        void should_extractMetricName_when_queryLatency() {
            IntentResult result = recognizer.recognize("查看当前延迟多少");

            assertThat(result.extractedEntities()).containsEntry("metricName", "latency");
        }

        @Test
        @DisplayName("查询吞吐量时提取 metricName=throughput")
        void should_extractMetricName_when_queryThroughput() {
            IntentResult result = recognizer.recognize("查看吞吐量多少");

            assertThat(result.extractedEntities()).containsEntry("metricName", "throughput");
        }

        @Test
        @DisplayName("查询连接数时提取 metricName=connections")
        void should_extractMetricName_when_queryConnections() {
            IntentResult result = recognizer.recognize("查看连接数多少");

            assertThat(result.extractedEntities()).containsEntry("metricName", "connections");
        }

        @Test
        @DisplayName("查询线程数时提取 metricName=threads")
        void should_extractMetricName_when_queryThreads() {
            IntentResult result = recognizer.recognize("查看线程数多少");

            assertThat(result.extractedEntities()).containsEntry("metricName", "threads");
        }

        @Test
        @DisplayName("查询 memory（英文）时提取 metricName=memory_usage")
        void should_extractMetricName_when_queryEnglishMemory() {
            IntentResult result = recognizer.recognize("查看 memory 使用率");

            assertThat(result.extractedEntities()).containsEntry("metricName", "memory_usage");
        }

        @Test
        @DisplayName("查询响应时间时提取 metricName=latency")
        void should_extractMetricName_when_queryResponseTime() {
            IntentResult result = recognizer.recognize("查看响应时间多少");

            assertThat(result.extractedEntities()).containsEntry("metricName", "latency");
        }

        @Test
        @DisplayName("查询请求量时提取 metricName=qps")
        void should_extractMetricName_when_queryRequestCount() {
            IntentResult result = recognizer.recognize("查看请求量多少");

            assertThat(result.extractedEntities()).containsEntry("metricName", "qps");
        }

        @Test
        @DisplayName("无指标关键词时实体为空")
        void should_returnEmptyEntities_when_noMetricKeyword() {
            IntentResult result = recognizer.recognize("如何配置 Nginx");

            assertThat(result.extractedEntities()).isEmpty();
        }
    }

    @Nested
    @DisplayName("未命中与异常")
    class NoMatchAndException {

        @Test
        @DisplayName("无规则命中时返回 UNKNOWN 低置信度")
        void should_returnUnknownLowConfidence_when_noRuleMatch() {
            IntentResult result = recognizer.recognize("今天天气不错");

            assertThat(result.intentType()).isEqualTo(IntentType.UNKNOWN);
            assertThat(result.confidence()).isLessThan(0.5);
        }

        @Test
        @DisplayName("输入为 null 时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputNull() {
            assertThatThrownBy(() -> recognizer.recognize(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("输入为空白时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputBlank() {
            assertThatThrownBy(() -> recognizer.recognize("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("getLayer 返回 L1_REGEX")
    void should_returnL1Regex_when_getLayerCalled() {
        assertThat(recognizer.getLayer()).isEqualTo("L1_REGEX");
    }
}
