package com.smartops.agent.intent;

import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import com.smartops.infrastructure.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link L4LLMRecognizer} 单元测试。
 *
 * <p>验证 L4 LLM 兜底识别器：ChatService.chatWithSystemPrompt 调用
 * （通过审计漏斗）、JSON 意图/真实置信度解析、置信度解析失败回退（0.5）、
 * 关键词匹配兜底（0.4）、异常容错。ChatService 被 Mock，不真实调用 LLM。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class L4LLMRecognizerTest {

    private ChatService chatService;
    private L4LLMRecognizer recognizer;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        recognizer = new L4LLMRecognizer(chatService);
    }

    @Nested
    @DisplayName("正常识别与真实置信度")
    class NormalRecognition {

        @Test
        @DisplayName("LLM 返回合法 JSON 时解析意图与真实置信度")
        void should_parseRealConfidence_when_llmReturnsValidJson() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("{\"intent\":\"QUERY_METRIC\",\"confidence\":0.92}");

            IntentResult result = recognizer.recognize("查询 CPU 使用率");

            assertThat(result.intentType()).isEqualTo(IntentType.QUERY_METRIC);
            assertThat(result.confidence()).isEqualTo(0.92);
            assertThat(result.source()).isEqualTo("L4_LLM");
        }

        @Test
        @DisplayName("LLM 返回整数置信度 1 时解析为 1.0")
        void should_parseOne_when_confidenceIsIntegerOne() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("{\"intent\":\"EXECUTE_OPERATION\",\"confidence\":1}");

            IntentResult result = recognizer.recognize("重启订单服务");

            assertThat(result.intentType()).isEqualTo(IntentType.EXECUTE_OPERATION);
            assertThat(result.confidence()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("置信度字段缺失时回退 0.5")
        void should_fallback05_when_confidenceMissing() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("{\"intent\":\"ROOT_CAUSE\"}");

            IntentResult result = recognizer.recognize("为什么服务响应变慢了");

            assertThat(result.intentType()).isEqualTo(IntentType.ROOT_CAUSE);
            assertThat(result.confidence()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("置信度越界（1.5）时回退 0.5")
        void should_fallback05_when_confidenceOutOfRange() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("{\"intent\":\"ROOT_CAUSE\",\"confidence\":1.5}");

            IntentResult result = recognizer.recognize("为什么服务响应变慢了");

            assertThat(result.intentType()).isEqualTo(IntentType.ROOT_CAUSE);
            assertThat(result.confidence()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("调用 chatWithSystemPrompt 时传递 systemPrompt 与 userInput")
        void should_delegateToChatService_when_recognize() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("{\"intent\":\"QUERY_METRIC\",\"confidence\":0.8}");

            recognizer.recognize("查询内存使用率");

            verify(chatService).chatWithSystemPrompt(
                    contains("QUERY_METRIC"), eq("查询内存使用率"));
        }
    }

    @Nested
    @DisplayName("异常容错")
    class ErrorHandling {

        @Test
        @DisplayName("LLM 返回非 JSON 时降级为关键词匹配（置信度 0.4）")
        void should_fallbackKeyword_when_llmReturnsInvalidJson() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("这不是 JSON 格式的响应");

            IntentResult result = recognizer.recognize("重启订单服务");

            assertThat(result.intentType()).isEqualTo(IntentType.EXECUTE_OPERATION);
            assertThat(result.confidence()).isEqualTo(0.4);
        }

        @Test
        @DisplayName("LLM 返回未知意图类型时降级为关键词匹配")
        void should_fallbackKeyword_when_llmReturnsUnknownIntent() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("{\"intent\":\"INVALID_TYPE\",\"confidence\":0.9}");

            IntentResult result = recognizer.recognize("CPU 使用率多少");

            assertThat(result.intentType()).isEqualTo(IntentType.QUERY_METRIC);
            assertThat(result.confidence()).isEqualTo(0.4);
        }

        @Test
        @DisplayName("LLM 返回 null 时降级为关键词匹配")
        void should_fallbackKeyword_when_llmReturnsNull() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn(null);

            IntentResult result = recognizer.recognize("如何配置 Nginx");

            assertThat(result.intentType()).isEqualTo(IntentType.KNOWLEDGE_QA);
        }

        @Test
        @DisplayName("关键词也无法匹配时返回 UNKNOWN（置信度 0.1）")
        void should_returnUnknown_when_noKeywordMatch() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("invalid");

            IntentResult result = recognizer.recognize("今天天气不错");

            assertThat(result.intentType()).isEqualTo(IntentType.UNKNOWN);
            assertThat(result.confidence()).isEqualTo(0.1);
        }

        @Test
        @DisplayName("LLM 调用异常时返回 UNKNOWN（置信度 0.1），不影响 Pipeline")
        void should_returnUnknown_when_llmThrows() {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenThrow(new RuntimeException("LLM 服务不可用"));

            IntentResult result = recognizer.recognize("查询 CPU");

            assertThat(result.intentType()).isEqualTo(IntentType.UNKNOWN);
            assertThat(result.confidence()).isEqualTo(0.1);
            assertThat(result.source()).isEqualTo("L4_LLM");
        }
    }

    @Nested
    @DisplayName("关键词兜底映射")
    class KeywordFallbackMapping {

        @ParameterizedTest
        @CsvSource({
                "CPU 使用率多少, QUERY_METRIC",
                "收到一条告警, ANALYZE_ALERT",
                "为什么响应变慢, ROOT_CAUSE",
                "重启订单服务, EXECUTE_OPERATION",
                "如何配置负载均衡, KNOWLEDGE_QA"
        })
        @DisplayName("LLM 输出不可解析时按关键词映射意图")
        void should_mapByKeyword_when_llmOutputUnparseable(String input, IntentType expected) {
            when(chatService.chatWithSystemPrompt(anyString(), anyString()))
                    .thenReturn("unparseable");

            IntentResult result = recognizer.recognize(input);

            assertThat(result.intentType()).isEqualTo(expected);
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
    @DisplayName("getLayer 返回 L4_LLM")
    void should_returnL4Llm_when_getLayerCalled() {
        assertThat(recognizer.getLayer()).isEqualTo("L4_LLM");
    }
}
