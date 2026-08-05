package com.smartops.infrastructure.advisor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link BoundedToolExecutionEligibilityChecker} 与 {@link ToolCallRoundGate} 单元测试。
 *
 * <p>验证工具调用轮次上限的强制执行、按请求覆盖、轮次记录与线程状态清理。
 * ChatResponse 被 Mock，不真实调用 LLM。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class BoundedToolExecutionEligibilityCheckerTest {

    @AfterEach
    void tearDown() {
        // 防止线程级状态跨用例泄露
        ToolCallRoundGate.clearRequest();
    }

    /** 构造含工具调用的模拟响应。 */
    private ChatResponse toolCallResponse() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.hasToolCalls()).thenReturn(true);
        return response;
    }

    /** 构造不含工具调用的模拟响应。 */
    private ChatResponse plainResponse() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.hasToolCalls()).thenReturn(false);
        return response;
    }

    @Nested
    @DisplayName("构造校验")
    class Construction {

        @Test
        @DisplayName("默认上限为非正数时抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_defaultMaxRoundsNotPositive() {
            assertThatThrownBy(() -> new BoundedToolExecutionEligibilityChecker(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BoundedToolExecutionEligibilityChecker(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("默认上限可通过 getDefaultMaxRounds 读取")
        void should_exposeDefaultMaxRounds_when_constructed() {
            assertThat(new BoundedToolExecutionEligibilityChecker(7).getDefaultMaxRounds()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("资格判定（apply）")
    class Eligibility {

        @Test
        @DisplayName("null 响应判定为不具备工具执行资格，并记录 0 轮")
        void should_returnFalse_when_responseNull() {
            BoundedToolExecutionEligibilityChecker checker = new BoundedToolExecutionEligibilityChecker(3);

            assertThat(checker.apply(null)).isFalse();
            assertThat(ToolCallRoundGate.lastCompletedRounds()).isZero();
        }

        @Test
        @DisplayName("无工具调用的响应判定为不具备资格，循环自然结束")
        void should_returnFalse_when_responseHasNoToolCalls() {
            BoundedToolExecutionEligibilityChecker checker = new BoundedToolExecutionEligibilityChecker(3);

            assertThat(checker.apply(plainResponse())).isFalse();
            assertThat(ToolCallRoundGate.lastCompletedRounds()).isZero();
        }

        @Test
        @DisplayName("未超上限时工具调用响应判定为具备资格")
        void should_returnTrue_when_underCap() {
            BoundedToolExecutionEligibilityChecker checker = new BoundedToolExecutionEligibilityChecker(2);

            assertThat(checker.apply(toolCallResponse())).isTrue();
            assertThat(checker.apply(toolCallResponse())).isTrue();
        }

        @Test
        @DisplayName("达到上限后拒绝继续执行工具调用，并记录已完成轮次")
        void should_returnFalse_when_capExceeded() {
            BoundedToolExecutionEligibilityChecker checker = new BoundedToolExecutionEligibilityChecker(2);

            assertThat(checker.apply(toolCallResponse())).isTrue();
            assertThat(checker.apply(toolCallResponse())).isTrue();
            // 第 3 轮超过上限 2，强制终止
            assertThat(checker.apply(toolCallResponse())).isFalse();
            assertThat(ToolCallRoundGate.lastCompletedRounds()).isEqualTo(2);
        }

        @Test
        @DisplayName("循环结束后计数重置，下一请求从第 1 轮重新开始")
        void should_resetCount_when_roundFinished() {
            BoundedToolExecutionEligibilityChecker checker = new BoundedToolExecutionEligibilityChecker(1);

            assertThat(checker.apply(toolCallResponse())).isTrue();
            assertThat(checker.apply(plainResponse())).isFalse();
            assertThat(ToolCallRoundGate.lastCompletedRounds()).isEqualTo(1);

            // 新一轮请求：计数已重置，第 1 轮再次放行
            assertThat(checker.apply(toolCallResponse())).isTrue();
        }
    }

    @Nested
    @DisplayName("按请求覆盖（ToolCallRoundGate）")
    class RequestOverride {

        @Test
        @DisplayName("startRequest 设置的覆盖值优先于检查器默认上限")
        void should_honorOverride_when_startRequestSet() {
            BoundedToolExecutionEligibilityChecker checker = new BoundedToolExecutionEligibilityChecker(10);
            ToolCallRoundGate.startRequest(1);

            assertThat(checker.apply(toolCallResponse())).isTrue();
            // 覆盖上限为 1，第 2 轮即被拒绝
            assertThat(checker.apply(toolCallResponse())).isFalse();
            assertThat(ToolCallRoundGate.lastCompletedRounds()).isEqualTo(1);
        }

        @Test
        @DisplayName("startRequest 重置上一请求的轮次记录")
        void should_resetLastRounds_when_startRequest() {
            BoundedToolExecutionEligibilityChecker checker = new BoundedToolExecutionEligibilityChecker(3);
            checker.apply(toolCallResponse());
            checker.apply(plainResponse());
            assertThat(ToolCallRoundGate.lastCompletedRounds()).isEqualTo(1);

            ToolCallRoundGate.startRequest(5);

            assertThat(ToolCallRoundGate.lastCompletedRounds()).isZero();
        }

        @Test
        @DisplayName("startRequest 拒绝非正数上限")
        void should_throwIllegalArg_when_startRequestNonPositive() {
            assertThatThrownBy(() -> ToolCallRoundGate.startRequest(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ToolCallRoundGate.startRequest(-3))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("未设置覆盖值时生效默认上限")
        void should_useDefault_when_noOverride() {
            assertThat(ToolCallRoundGate.effectiveMaxRounds(5)).isEqualTo(5);
        }

        @Test
        @DisplayName("设置覆盖值后生效覆盖上限，clearRequest 后恢复默认")
        void should_restoreDefault_when_cleared() {
            ToolCallRoundGate.startRequest(3);
            assertThat(ToolCallRoundGate.effectiveMaxRounds(5)).isEqualTo(3);

            ToolCallRoundGate.clearRequest();

            assertThat(ToolCallRoundGate.effectiveMaxRounds(5)).isEqualTo(5);
            assertThat(ToolCallRoundGate.lastCompletedRounds()).isZero();
        }
    }
}
