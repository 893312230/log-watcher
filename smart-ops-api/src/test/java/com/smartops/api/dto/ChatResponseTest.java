package com.smartops.api.dto;

import com.smartops.common.enums.AgentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatResponse} DTO 测试。
 *
 * <p>验证对话响应的构造、工厂方法、字段访问与 equals/hashCode。
 * 阶段二新增执行元数据字段（mode, iterations, success, errorMessage）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ChatResponseTest {

    @Nested
    @DisplayName("构造器测试")
    class Constructor {

        @Test
        @DisplayName("全字段构造时正确携带所有值")
        void should_carryAllFields_when_constructed() {
            LocalDateTime now = LocalDateTime.now();

            ChatResponse response = new ChatResponse("conv-1", "CPU 使用率 65%", now,
                    AgentMode.REACT, 3, true, null, false, null);

            assertThat(response.conversationId()).isEqualTo("conv-1");
            assertThat(response.reply()).isEqualTo("CPU 使用率 65%");
            assertThat(response.timestamp()).isEqualTo(now);
            assertThat(response.mode()).isEqualTo(AgentMode.REACT);
            assertThat(response.iterations()).isEqualTo(3);
            assertThat(response.success()).isTrue();
            assertThat(response.errorMessage()).isNull();
        }

        @Test
        @DisplayName("null 字段也能正确构造")
        void should_allowNullFields_when_constructed() {
            ChatResponse response = new ChatResponse(null, null, null,
                    null, 0, false, null, false, null);

            assertThat(response.conversationId()).isNull();
            assertThat(response.reply()).isNull();
            assertThat(response.timestamp()).isNull();
            assertThat(response.mode()).isNull();
            assertThat(response.iterations()).isZero();
            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).isNull();
        }

        @Test
        @DisplayName("失败响应携带错误信息")
        void should_carryErrorMessage_when_failureResponse() {
            ChatResponse response = new ChatResponse("conv-1", null, LocalDateTime.now(),
                    AgentMode.PLAN_AND_SOLVE, 0, false, "LLM 服务不可用", false, null);

            assertThat(response.reply()).isNull();
            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).isEqualTo("LLM 服务不可用");
        }
    }

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethods {

        @Test
        @DisplayName("success 工厂方法构建成功响应")
        void should_buildSuccessResponse_when_successFactoryCalled() {
            ChatResponse response = ChatResponse.success("conv-1", "分析完成",
                    AgentMode.REACT, 2);

            assertThat(response.conversationId()).isEqualTo("conv-1");
            assertThat(response.reply()).isEqualTo("分析完成");
            assertThat(response.mode()).isEqualTo(AgentMode.REACT);
            assertThat(response.iterations()).isEqualTo(2);
            assertThat(response.success()).isTrue();
            assertThat(response.errorMessage()).isNull();
            assertThat(response.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("failure 工厂方法构建失败响应")
        void should_buildFailureResponse_when_failureFactoryCalled() {
            ChatResponse response = ChatResponse.failure("conv-1",
                    AgentMode.PLAN_AND_SOLVE, "执行超时");

            assertThat(response.conversationId()).isEqualTo("conv-1");
            assertThat(response.reply()).isNull();
            assertThat(response.mode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            assertThat(response.iterations()).isZero();
            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).isEqualTo("执行超时");
            assertThat(response.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("pendingConfirmation 工厂方法构建待确认响应")
        void should_buildPendingResponse_when_pendingFactoryCalled() {
            ChatResponse response = ChatResponse.pendingConfirmation(
                    "conv-1", "token-abc", "高风险操作需要人工确认");

            assertThat(response.conversationId()).isEqualTo("conv-1");
            assertThat(response.reply()).isNull();
            assertThat(response.success()).isFalse();
            assertThat(response.pendingConfirmation()).isTrue();
            assertThat(response.confirmationToken()).isEqualTo("token-abc");
            assertThat(response.errorMessage()).contains("人工确认");
            assertThat(response.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("success/failure 工厂的确认相关字段为默认值")
        void should_defaultConfirmationFields_when_successOrFailureFactory() {
            ChatResponse success = ChatResponse.success("conv-1", "回复", AgentMode.REACT, 1);
            ChatResponse failure = ChatResponse.failure("conv-1", AgentMode.REACT, "错误");

            assertThat(success.pendingConfirmation()).isFalse();
            assertThat(success.confirmationToken()).isNull();
            assertThat(failure.pendingConfirmation()).isFalse();
            assertThat(failure.confirmationToken()).isNull();
        }
    }

    @Nested
    @DisplayName("equals 与 hashCode 测试")
    class EqualsAndHashCode {

        @Test
        @DisplayName("所有字段相同时 equals 返回 true")
        void should_beEqual_when_allFieldsSame() {
            LocalDateTime ts = LocalDateTime.of(2026, 7, 18, 10, 0);

            ChatResponse r1 = new ChatResponse("conv-1", "回复", ts,
                    AgentMode.REACT, 1, true, null, false, null);
            ChatResponse r2 = new ChatResponse("conv-1", "回复", ts,
                    AgentMode.REACT, 1, true, null, false, null);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("任一字段不同时 equals 返回 false")
        void should_notBeEqual_when_anyFieldDifferent() {
            LocalDateTime ts = LocalDateTime.now();

            ChatResponse r1 = new ChatResponse("conv-1", "回复", ts,
                    AgentMode.REACT, 1, true, null, false, null);
            ChatResponse r2 = new ChatResponse("conv-1", "回复", ts,
                    AgentMode.PLAN_AND_SOLVE, 1, true, null, false, null);

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("iterations 不同时不相等")
        void should_notBeEqual_when_iterationsDifferent() {
            LocalDateTime ts = LocalDateTime.now();

            ChatResponse r1 = new ChatResponse("conv-1", "回复", ts,
                    AgentMode.REACT, 1, true, null, false, null);
            ChatResponse r2 = new ChatResponse("conv-1", "回复", ts,
                    AgentMode.REACT, 3, true, null, false, null);

            assertThat(r1).isNotEqualTo(r2);
        }
    }

    @Test
    @DisplayName("toString 包含字段信息")
    void should_containFieldInfo_when_toStringCalled() {
        ChatResponse response = new ChatResponse("conv-1", "回复", LocalDateTime.now(),
                AgentMode.REACT, 2, true, null, false, null);

        String str = response.toString();

        assertThat(str).contains("conv-1");
        assertThat(str).contains("回复");
        assertThat(str).contains("REACT");
    }
}
