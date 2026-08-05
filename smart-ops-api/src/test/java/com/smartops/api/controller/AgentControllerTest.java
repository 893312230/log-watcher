package com.smartops.api.controller;

import com.smartops.api.dto.ChatRequest;
import com.smartops.api.dto.ChatResponse;
import com.smartops.agent.router.AgentRouter;
import com.smartops.agent.security.ConfirmationTokenStore;
import com.smartops.agent.security.InputFilter;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.exception.SecurityViolationException;
import com.smartops.common.model.AgentExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentController} 单元测试。
 *
 * <p>阶段二集成 AgentRouter 后，验证对话接口的会话 ID 生成、
 * 路由决策调用、执行结果映射等核心行为。
 * AgentRouter 被 Mock，不真实调用 LLM 与意图识别。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AgentControllerTest {

    private AgentRouter agentRouter;
    private ConfirmationTokenStore confirmationTokenStore;
    private AgentController controller;

    @BeforeEach
    void setUp() {
        agentRouter = mock(AgentRouter.class);
        confirmationTokenStore = new ConfirmationTokenStore();
        controller = new AgentController(agentRouter, confirmationTokenStore,
                emptyProvider());
    }

    /**
     * 构造一个成功的 ReAct 执行结果用于测试。
     */
    private AgentExecutionResult successReActResult() {
        return AgentExecutionResult.success("模拟回复内容", AgentMode.REACT, 2,
                List.of("步骤1: 查询指标", "步骤2: 生成回复"));
    }

    /**
     * 构造一个成功的 Plan-and-Solve 执行结果用于测试。
     */
    private AgentExecutionResult successPlanResult() {
        return AgentExecutionResult.success("计划执行完成", AgentMode.PLAN_AND_SOLVE, 3,
                List.of("步骤1", "步骤2", "步骤3"));
    }

    /**
     * 构造一个失败的执行结果用于测试。
     */
    private AgentExecutionResult failureResult() {
        return AgentExecutionResult.failure(AgentMode.REACT, 0, List.of(),
                "LLM 服务不可用");
    }

    @Nested
    @DisplayName("会话 ID 处理")
    class ConversationIdHandling {

        @Test
        @DisplayName("会话 ID 为 null 时自动生成 UUID")
        void should_generateUuid_when_conversationIdIsNull() {
            when(agentRouter.route(anyString(), anyString())).thenReturn(successReActResult());
            ChatRequest request = new ChatRequest(null, "查询 CPU 使用率", null);

            ChatResponse response = controller.chat(request);

            assertThat(response.conversationId()).isNotNull().isNotBlank();
            assertThat(response.conversationId()).matches("[0-9a-fA-F-]{36}");
        }

        @Test
        @DisplayName("会话 ID 为空白字符串时自动生成 UUID")
        void should_generateUuid_when_conversationIdIsBlank() {
            when(agentRouter.route(anyString(), anyString())).thenReturn(successReActResult());
            ChatRequest request = new ChatRequest("   ", "查询内存使用率", null);

            ChatResponse response = controller.chat(request);

            assertThat(response.conversationId()).isNotBlank();
            assertThat(response.conversationId()).isNotEqualTo("   ");
        }

        @Test
        @DisplayName("提供有效会话 ID 时沿用该 ID")
        void should_keepConversationId_when_validIdProvided() {
            when(agentRouter.route(anyString(), anyString())).thenReturn(successReActResult());
            String existingId = "conv-12345";
            ChatRequest request = new ChatRequest(existingId, "查询磁盘使用率", null);

            ChatResponse response = controller.chat(request);

            assertThat(response.conversationId()).isEqualTo(existingId);
        }
    }

    @Nested
    @DisplayName("路由决策与结果映射")
    class RoutingAndMapping {

        @Test
        @DisplayName("ReAct 成功结果正确映射为响应")
        void should_mapReActSuccessResult_when_routeReturnsSuccess() {
            when(agentRouter.route(anyString(), anyString())).thenReturn(successReActResult());
            ChatRequest request = new ChatRequest("conv-test", "查询 CPU", null);

            ChatResponse response = controller.chat(request);

            assertThat(response.reply()).isEqualTo("模拟回复内容");
            assertThat(response.mode()).isEqualTo(AgentMode.REACT);
            assertThat(response.iterations()).isEqualTo(2);
            assertThat(response.success()).isTrue();
            assertThat(response.errorMessage()).isNull();
            assertThat(response.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Plan-and-Solve 成功结果正确映射为响应")
        void should_mapPlanSuccessResult_when_routeReturnsPlanSuccess() {
            when(agentRouter.route(anyString(), anyString())).thenReturn(successPlanResult());
            ChatRequest request = new ChatRequest("conv-test", "重启服务并验证", null);

            ChatResponse response = controller.chat(request);

            assertThat(response.reply()).isEqualTo("计划执行完成");
            assertThat(response.mode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            assertThat(response.iterations()).isEqualTo(3);
            assertThat(response.success()).isTrue();
        }

        @Test
        @DisplayName("失败结果正确映射为错误响应")
        void should_mapFailureResult_when_routeReturnsFailure() {
            when(agentRouter.route(anyString(), anyString())).thenReturn(failureResult());
            ChatRequest request = new ChatRequest("conv-err", "触发错误", null);

            ChatResponse response = controller.chat(request);

            assertThat(response.reply()).isNull();
            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).isEqualTo("LLM 服务不可用");
            assertThat(response.mode()).isEqualTo(AgentMode.REACT);
            assertThat(response.iterations()).isZero();
        }
    }

    @Nested
    @DisplayName("消息传递")
    class MessagePassing {

        @Test
        @DisplayName("用户消息被正确传递给 AgentRouter")
        void should_passUserMessageToRouter_when_chat() {
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> convIdCaptor = ArgumentCaptor.forClass(String.class);
            when(agentRouter.route(messageCaptor.capture(), convIdCaptor.capture()))
                    .thenReturn(successReActResult());

            ChatRequest request = new ChatRequest("conv-test", "帮我分析 CPU 趋势", null);
            controller.chat(request);

            assertThat(messageCaptor.getValue()).isEqualTo("帮我分析 CPU 趋势");
            assertThat(convIdCaptor.getValue()).isEqualTo("conv-test");
        }

        @Test
        @DisplayName("自动生成的会话 ID 也传递给 AgentRouter")
        void should_passGeneratedConvIdToRouter_when_noConvIdProvided() {
            ArgumentCaptor<String> convIdCaptor = ArgumentCaptor.forClass(String.class);
            when(agentRouter.route(anyString(), convIdCaptor.capture()))
                    .thenReturn(successReActResult());

            ChatRequest request = new ChatRequest(null, "查询 QPS", null);
            controller.chat(request);

            assertThat(convIdCaptor.getValue()).isNotBlank().matches("[0-9a-fA-F-]{36}");
        }
    }

    @Nested
    @DisplayName("高危操作人工确认流程（IT-2）")
    class ConfirmationFlow {

        @Test
        @DisplayName("路由抛出安全违规异常时返回待确认响应并签发令牌")
        void should_returnPendingConfirmation_when_routerThrowsSecurityViolation() {
            when(agentRouter.route(anyString(), anyString()))
                    .thenThrow(new SecurityViolationException(
                            "SECURITY_CONFIRM_REQUIRED", "高风险操作需要人工确认: 重启订单服务"));
            ChatRequest request = new ChatRequest("conv-sec", "重启订单服务", null);

            ChatResponse response = controller.chat(request);

            assertThat(response.pendingConfirmation()).isTrue();
            assertThat(response.confirmationToken()).isNotBlank();
            assertThat(response.success()).isFalse();
            assertThat(response.reply()).isNull();
            assertThat(response.errorMessage()).contains("人工确认");
        }

        @Test
        @DisplayName("携带有效令牌与原始消息重提后执行成功")
        void should_executeSuccessfully_when_validTokenResubmitted() {
            // 第一次请求：触发安全门，获得令牌
            when(agentRouter.route(anyString(), anyString()))
                    .thenThrow(new SecurityViolationException(
                            "SECURITY_CONFIRM_REQUIRED", "高风险操作需要人工确认"))
                    .thenReturn(successReActResult());
            ChatRequest firstRequest = new ChatRequest("conv-sec", "重启订单服务", null);
            ChatResponse pending = controller.chat(firstRequest);
            String token = pending.confirmationToken();

            // 第二次请求：携带令牌与相同消息重提
            ChatRequest retryRequest = new ChatRequest("conv-sec", "重启订单服务", token);
            ChatResponse response = controller.chat(retryRequest);

            assertThat(response.success()).isTrue();
            assertThat(response.pendingConfirmation()).isFalse();
            assertThat(response.reply()).isEqualTo("模拟回复内容");
        }

        @Test
        @DisplayName("令牌为伪造值时返回失败且不调用路由")
        void should_returnFailure_when_tokenInvalid() {
            ChatRequest request = new ChatRequest("conv-sec", "重启订单服务", "forged-token");

            ChatResponse response = controller.chat(request);

            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).contains("确认令牌无效");
            verify(agentRouter, never()).route(anyString(), anyString());
        }

        @Test
        @DisplayName("令牌对应的消息被篡改时验证失败")
        void should_returnFailure_when_messageTampered() {
            when(agentRouter.route(anyString(), anyString()))
                    .thenThrow(new SecurityViolationException(
                            "SECURITY_CONFIRM_REQUIRED", "高风险操作需要人工确认"));
            ChatResponse pending = controller.chat(new ChatRequest("conv-sec", "重启订单服务", null));
            String token = pending.confirmationToken();

            // 用同一令牌但不同消息重提（企图确认其他操作）
            ChatResponse response = controller.chat(
                    new ChatRequest("conv-sec", "删除数据库", token));

            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).contains("确认令牌无效");
            verify(agentRouter, org.mockito.Mockito.times(1)).route(anyString(), anyString());
        }

        @Test
        @DisplayName("令牌一次性：消费后再次使用验证失败")
        void should_returnFailure_when_tokenReused() {
            when(agentRouter.route(anyString(), anyString()))
                    .thenThrow(new SecurityViolationException(
                            "SECURITY_CONFIRM_REQUIRED", "高风险操作需要人工确认"))
                    .thenReturn(successReActResult());
            ChatResponse pending = controller.chat(new ChatRequest("conv-sec", "重启订单服务", null));
            String token = pending.confirmationToken();
            controller.chat(new ChatRequest("conv-sec", "重启订单服务", token));

            // 同一令牌第二次使用
            ChatResponse response = controller.chat(new ChatRequest("conv-sec", "重启订单服务", token));

            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).contains("确认令牌无效");
        }

        @Test
        @DisplayName("空白令牌视为未携带，按普通请求路由")
        void should_routeNormally_when_tokenBlank() {
            when(agentRouter.route(anyString(), anyString())).thenReturn(successReActResult());
            ChatRequest request = new ChatRequest("conv-blank", "查询 CPU", "   ");

            ChatResponse response = controller.chat(request);

            assertThat(response.success()).isTrue();
            assertThat(response.pendingConfirmation()).isFalse();
            verify(agentRouter).route("查询 CPU", "conv-blank");
        }
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override public T getObject() { return null; }
            @Override public T getIfAvailable() { return null; }
        };
    }
}
