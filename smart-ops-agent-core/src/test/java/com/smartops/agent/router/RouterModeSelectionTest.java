package com.smartops.agent.router;

import com.smartops.agent.intent.ConflictResolver;
import com.smartops.agent.intent.IntentPipeline;
import com.smartops.agent.intent.L1RegexRecognizer;
import com.smartops.agent.intent.L2KeywordRecognizer;
import com.smartops.agent.intent.L4LLMRecognizer;
import com.smartops.agent.orchestrator.SupervisorAgent;
import com.smartops.agent.plan.PlanAndSolveExecutor;
import com.smartops.agent.react.ReActExecutor;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 路由模式选择集成测试（IT-3）。
 *
 * <p>以真实 L1/L2 识别器 + ConflictResolver + TaskAnalyzer 组成完整意图-分析链路
 * （仅 L4 LLM 与执行器 Mock，不连真实 LLM/MCP/DB），验证三类代表输入的
 * 端到端模式选择：查询 → REACT、多操作 → PLAN_AND_SOLVE、根因 → REACT。</p>
 *
 * <p>对应 agent.md 阶段二路由修复（ADR-011 置信度体系 + TaskAnalyzer 探索性收紧）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class RouterModeSelectionTest {

    private L4LLMRecognizer l4;
    private AgentRouter router;

    @BeforeEach
    void setUp() {
        l4 = mock(L4LLMRecognizer.class);
        IntentPipeline pipeline = new IntentPipeline(
                new L1RegexRecognizer(),
                new L2KeywordRecognizer(),
                l4,
                new ConflictResolver()
        );
        router = new AgentRouter(
                pipeline,
                new TaskAnalyzer(),
                mock(ReActExecutor.class),
                mock(PlanAndSolveExecutor.class),
                mock(SupervisorAgent.class)
        );
    }

    @Test
    @DisplayName("查询类输入经完整链路选择 REACT")
    void should_selectReAct_when_queryInput() {
        // L1 宽泛兜底（0.4）不短路，L2 词频置信度不足，落到 L4 兜底
        when(l4.recognize(anyString()))
                .thenReturn(new IntentResult(IntentType.QUERY_METRIC, 0.9, IntentResult.SOURCE_L4_LLM, null));

        AgentRouter.RoutingDecision decision = router.getRoutingDecision("查询 CPU 使用率");

        assertThat(decision.intentResult().intentType()).isEqualTo(IntentType.QUERY_METRIC);
        assertThat(decision.selectedMode()).isEqualTo(AgentMode.REACT);
        assertThat(decision.useSupervisor()).isFalse();
    }

    @Test
    @DisplayName("多步骤操作输入经完整链路选择 PLAN_AND_SOLVE")
    void should_selectPlanAndSolve_when_multiStepOperationInput() {
        // L1 具体规则（重启/清理）0.9 短路；2 动作 + 连接词 → 有依赖的多步骤任务
        AgentRouter.RoutingDecision decision = router.getRoutingDecision("重启订单服务然后清理缓存");

        assertThat(decision.intentResult().intentType()).isEqualTo(IntentType.EXECUTE_OPERATION);
        assertThat(decision.selectedMode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
        assertThat(decision.useSupervisor()).isFalse();
    }

    @Test
    @DisplayName("根因分析输入经完整链路选择 REACT")
    void should_selectReAct_when_rootCauseInput() {
        // L1 具体规则（为什么）0.9 短路；探索性任务 → REACT
        AgentRouter.RoutingDecision decision = router.getRoutingDecision("为什么服务响应变慢");

        assertThat(decision.intentResult().intentType()).isEqualTo(IntentType.ROOT_CAUSE);
        assertThat(decision.selectedMode()).isEqualTo(AgentMode.REACT);
        assertThat(decision.useSupervisor()).isFalse();
    }
}
