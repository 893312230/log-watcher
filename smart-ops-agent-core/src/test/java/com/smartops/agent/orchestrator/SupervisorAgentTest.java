package com.smartops.agent.orchestrator;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.common.enums.AgentMode;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.TaskStatus;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentExecutionResult;
import com.smartops.common.model.SubTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SupervisorAgent} 单元测试。
 *
 * <p>验证 Supervisor Agent 的核心契约：
 * <ul>
 *   <li>任务分解（decompose）：各类关键词组合匹配 MONITOR/ANALYZE/EXECUTE/KNOWLEDGE</li>
 *   <li>编排流程（orchestrate）：分解 → 分发 → 聚合</li>
 *   <li>结果聚合：全部成功、全部失败、部分失败的汇总提示</li>
 *   <li>异常处理：null/空白输入、无匹配关键词</li>
 *   <li>conversationId 为 null 时自动生成 parentTaskId</li>
 * </ul></p>
 *
 * <p>对应 agent.md 阶段三特性5（Supervisor Agent）。</p>
 *
 * <p><b>测试策略</b>：
 * <ul>
 *   <li>Mock {@link TaskDispatcher} 与 {@link AgentCardRegistry}，隔离所有依赖</li>
 *   <li>使用 AssertJ 断言，遵循 Arrange-Act-Assert 三段式</li>
 *   <li>使用 {@code @Nested} 分组，{@code @DisplayName} 中文描述</li>
 *   <li>测试方法命名 {@code should_{期望行为}_when_{前置条件}}</li>
 *   <li>decompose 为包级可见，可直接调用测试；containsAny 私有方法通过反射覆盖防御分支</li>
 * </ul></p>
 *
 * @author smartops
 * @since 1.0.0
 */
class SupervisorAgentTest {

    private TaskDispatcher dispatcher;
    private AgentCardRegistry registry;
    private SupervisorAgent supervisor;

    @BeforeEach
    void setUp() {
        dispatcher = mock(TaskDispatcher.class);
        registry = mock(AgentCardRegistry.class);
        supervisor = new SupervisorAgent(dispatcher, registry);
    }

    @Nested
    @DisplayName("构造")
    class Construction {

        @Test
        @DisplayName("dispatcher 为 null 抛出 NullPointerException")
        void should_throwNpe_when_dispatcherIsNull() {
            assertThatThrownBy(() -> new SupervisorAgent(null, registry))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("dispatcher");
        }

        @Test
        @DisplayName("registry 为 null 抛出 NullPointerException")
        void should_throwNpe_when_registryIsNull() {
            assertThatThrownBy(() -> new SupervisorAgent(dispatcher, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("registry");
        }
    }

    @Nested
    @DisplayName("decompose 任务分解")
    class Decompose {

        @Test
        @DisplayName("包含监控关键词时生成 MONITOR 子任务")
        void should_generateMonitorTask_when_inputContainsMonitorKeyword() {
            List<SubTask> tasks = supervisor.decompose("查询CPU指标", "parent-001");

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).targetRole()).isEqualTo(AgentRole.MONITOR);
            assertThat(tasks.get(0).parentTaskId()).isEqualTo("parent-001");
            assertThat(tasks.get(0).priority()).isEqualTo(1);
            assertThat(tasks.get(0).instruction()).contains("查询CPU指标");
            assertThat(tasks.get(0).status()).isEqualTo(TaskStatus.CREATED);
            assertThat(tasks.get(0).result()).isNull();
        }

        @Test
        @DisplayName("包含告警关键词时生成 MONITOR 子任务")
        void should_generateMonitorTask_when_inputContainsAlertKeyword() {
            List<SubTask> tasks = supervisor.decompose("处理告警", "parent-001");

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).targetRole()).isEqualTo(AgentRole.MONITOR);
        }

        @Test
        @DisplayName("包含分析关键词时生成 ANALYZE 子任务")
        void should_generateAnalyzeTask_when_inputContainsAnalyzeKeyword() {
            List<SubTask> tasks = supervisor.decompose("分析根因", "parent-001");

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).targetRole()).isEqualTo(AgentRole.ANALYZE);
            assertThat(tasks.get(0).priority()).isEqualTo(2);
        }

        @Test
        @DisplayName("包含日志关键词时生成 ANALYZE 子任务")
        void should_generateAnalyzeTask_when_inputContainsLogKeyword() {
            List<SubTask> tasks = supervisor.decompose("查看日志", "parent-001");

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).targetRole()).isEqualTo(AgentRole.ANALYZE);
        }

        @Test
        @DisplayName("包含执行关键词时生成 EXECUTE 子任务")
        void should_generateExecuteTask_when_inputContainsExecuteKeyword() {
            List<SubTask> tasks = supervisor.decompose("重启服务", "parent-001");

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).targetRole()).isEqualTo(AgentRole.EXECUTE);
            assertThat(tasks.get(0).priority()).isEqualTo(3);
        }

        @Test
        @DisplayName("包含扩缩容关键词时生成 EXECUTE 子任务")
        void should_generateExecuteTask_when_inputContainsScaleKeyword() {
            List<SubTask> tasks = supervisor.decompose("扩缩容集群", "parent-001");

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).targetRole()).isEqualTo(AgentRole.EXECUTE);
        }

        @Test
        @DisplayName("包含知识关键词时生成 KNOWLEDGE 子任务")
        void should_generateKnowledgeTask_when_inputContainsKnowledgeKeyword() {
            List<SubTask> tasks = supervisor.decompose("查询运维知识文档", "parent-001");

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).targetRole()).isEqualTo(AgentRole.KNOWLEDGE);
            assertThat(tasks.get(0).priority()).isEqualTo(4);
        }

        @Test
        @DisplayName("包含怎么关键词时生成 KNOWLEDGE 子任务")
        void should_generateKnowledgeTask_when_inputContainsHowKeyword() {
            List<SubTask> tasks = supervisor.decompose("怎么配置Nginx", "parent-001");

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).targetRole()).isEqualTo(AgentRole.KNOWLEDGE);
        }

        @Test
        @DisplayName("包含所有领域关键词时生成 4 个子任务，按优先级排序")
        void should_generateFourTasks_when_inputContainsAllKeywords() {
            List<SubTask> tasks = supervisor.decompose(
                    "监控指标分析根因重启服务知识文档", "parent-001");

            assertThat(tasks).hasSize(4);
            assertThat(tasks).extracting(SubTask::targetRole)
                    .containsExactly(
                            AgentRole.MONITOR,
                            AgentRole.ANALYZE,
                            AgentRole.EXECUTE,
                            AgentRole.KNOWLEDGE);
            assertThat(tasks).extracting(SubTask::priority)
                    .containsExactly(1, 2, 3, 4);
        }

        @Test
        @DisplayName("无任何关键词时返回空列表")
        void should_returnEmptyList_when_noKeywordMatches() {
            List<SubTask> tasks = supervisor.decompose("你好世界", "parent-001");

            assertThat(tasks).isEmpty();
        }

        @Test
        @DisplayName("英文关键词同样匹配（大小写不敏感）")
        void should_matchEnglishKeywords_when_inputIsEnglish() {
            List<SubTask> tasks = supervisor.decompose(
                    "check metric and analyze root cause", "parent-001");

            assertThat(tasks).extracting(SubTask::targetRole)
                    .contains(AgentRole.MONITOR, AgentRole.ANALYZE);
        }

        @Test
        @DisplayName("英文 RESTART/SCALE/DEPLOY 匹配执行关键词")
        void should_matchEnglishExecuteKeywords_when_upperCase() {
            List<SubTask> tasks = supervisor.decompose(
                    "RESTART and SCALE and DEPLOY", "parent-001");

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).targetRole()).isEqualTo(AgentRole.EXECUTE);
        }

        @Test
        @DisplayName("每个子任务有唯一的 taskId")
        void should_haveUniqueTaskIds_when_multipleTasksGenerated() {
            List<SubTask> tasks = supervisor.decompose(
                    "监控指标分析根因重启服务知识文档", "parent-001");

            assertThat(tasks).hasSize(4);
            long uniqueIds = tasks.stream().map(SubTask::taskId).distinct().count();
            assertThat(uniqueIds).isEqualTo(4);
        }

        @Test
        @DisplayName("子任务的 instruction 包含原始用户输入")
        void should_containUserInput_when_subTaskInstructionGenerated() {
            String userInput = "监控CPU使用率";
            List<SubTask> tasks = supervisor.decompose(userInput, "parent-001");

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).instruction()).contains(userInput);
        }
    }

    @Nested
    @DisplayName("containsAny 私有方法（反射覆盖防御分支）")
    class ContainsAny {

        /**
         * 通过反射调用私有 containsAny 方法。
         *
         * @param input    输入文本
         * @param keywords 关键词数组
         * @return 是否包含任一关键词
         * @throws Exception 反射调用异常
         */
        private boolean invokeContainsAny(String input, String[] keywords) throws Exception {
            Method method = SupervisorAgent.class.getDeclaredMethod(
                    "containsAny", String.class, String[].class);
            method.setAccessible(true);
            return (boolean) method.invoke(supervisor, new Object[]{input, keywords});
        }

        @Test
        @DisplayName("keywords 为 null 时返回 false")
        void should_returnFalse_when_keywordsIsNull() throws Exception {
            assertThat(invokeContainsAny("test", null)).isFalse();
        }

        @Test
        @DisplayName("包含 null 元素的 keywords 跳过 null 后匹配非 null 关键词")
        void should_skipNullKeyword_when_arrayContainsNull() throws Exception {
            assertThat(invokeContainsAny("test", new String[]{null, "test"})).isTrue();
        }

        @Test
        @DisplayName("全为 null 元素的 keywords 返回 false")
        void should_returnFalse_when_allKeywordsAreNull() throws Exception {
            assertThat(invokeContainsAny("test", new String[]{null, null})).isFalse();
        }

        @Test
        @DisplayName("空 keywords 数组返回 false")
        void should_returnFalse_when_keywordsIsEmpty() throws Exception {
            assertThat(invokeContainsAny("test", new String[0])).isFalse();
        }

        @Test
        @DisplayName("不包含任何关键词时返回 false")
        void should_returnFalse_when_noKeywordMatches() throws Exception {
            assertThat(invokeContainsAny("hello", new String[]{"world", "foo"})).isFalse();
        }
    }

    @Nested
    @DisplayName("orchestrate 编排")
    class Orchestrate {

        @Test
        @DisplayName("userInput 为 null 抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputIsNull() {
            assertThatThrownBy(() -> supervisor.orchestrate(null, "conv-001"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }

        @Test
        @DisplayName("userInput 为空白抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_inputIsBlank() {
            assertThatThrownBy(() -> supervisor.orchestrate("   ", "conv-001"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户输入");
        }

        @Test
        @DisplayName("无匹配关键词时返回默认无法分解结果")
        void should_returnDefaultResult_when_noSubTasksGenerated() {
            AgentExecutionResult result = supervisor.orchestrate("你好世界", "conv-001");

            assertThat(result.success()).isTrue();
            assertThat(result.answer()).contains("无法分解该任务");
            assertThat(result.answer()).contains("请提供更具体的运维指令");
            assertThat(result.mode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
            assertThat(result.iterations()).isEqualTo(0);
            assertThat(result.steps()).isEmpty();
            verify(dispatcher, never()).dispatch(any(SubTask.class));
        }

        @Test
        @DisplayName("所有子任务成功时聚合结果包含全部成功提示")
        void should_returnAllSuccessResult_when_allSubTasksSucceed() {
            when(dispatcher.dispatch(any(SubTask.class))).thenReturn(
                    A2aResponse.success("r1", "t1", AgentRole.MONITOR, "监控结果"),
                    A2aResponse.success("r2", "t2", AgentRole.ANALYZE, "分析结果")
            );

            AgentExecutionResult result = supervisor.orchestrate(
                    "监控指标分析根因", "conv-001");

            assertThat(result.success()).isTrue();
            assertThat(result.iterations()).isEqualTo(2);
            assertThat(result.answer()).contains("成功 2 个");
            assertThat(result.answer()).contains("失败 0 个");
            assertThat(result.answer()).contains("所有子任务均已成功完成");
            assertThat(result.answer()).contains("监控结果");
            assertThat(result.answer()).contains("分析结果");
            assertThat(result.steps()).hasSize(2);
            assertThat(result.steps()).allMatch(step -> step.contains("SUCCESS"));
            assertThat(result.steps()).allMatch(step -> !step.contains("RUNNING"));
        }

        @Test
        @DisplayName("所有子任务失败时返回 success=false 且携带失败清单")
        void should_returnFailureResult_when_allSubTasksFail() {
            when(dispatcher.dispatch(any(SubTask.class))).thenReturn(
                    A2aResponse.failure("r1", "t1", AgentRole.MONITOR, "监控超时"),
                    A2aResponse.failure("r2", "t2", AgentRole.ANALYZE, "分析异常")
            );

            AgentExecutionResult result = supervisor.orchestrate(
                    "监控指标分析根因", "conv-001");

            assertThat(result.success()).isFalse();
            assertThat(result.answer()).isNull();
            assertThat(result.iterations()).isEqualTo(2);
            assertThat(result.errorMessage()).contains("成功 0 个");
            assertThat(result.errorMessage()).contains("失败 2 个");
            assertThat(result.errorMessage()).contains("所有子任务均执行失败");
            assertThat(result.errorMessage()).contains("失败: 监控超时");
            assertThat(result.errorMessage()).contains("失败: 分析异常");
            assertThat(result.steps()).allMatch(step -> step.contains("FAILED"));
        }

        @Test
        @DisplayName("部分子任务成功部分失败时聚合结果包含部分失败提示")
        void should_returnMixedResult_when_someSubTasksFail() {
            when(dispatcher.dispatch(any(SubTask.class))).thenReturn(
                    A2aResponse.success("r1", "t1", AgentRole.MONITOR, "监控结果"),
                    A2aResponse.failure("r2", "t2", AgentRole.ANALYZE, "分析异常")
            );

            AgentExecutionResult result = supervisor.orchestrate(
                    "监控指标分析根因", "conv-001");

            assertThat(result.success()).isTrue();
            assertThat(result.iterations()).isEqualTo(2);
            assertThat(result.answer()).contains("成功 1 个");
            assertThat(result.answer()).contains("失败 1 个");
            assertThat(result.answer()).contains("部分子任务执行失败");
            assertThat(result.answer()).contains("监控结果");
            assertThat(result.answer()).contains("失败: 分析异常");
        }

        @Test
        @DisplayName("conversationId 为 null 时使用自动生成的 parentTaskId")
        void should_useGeneratedId_when_conversationIdIsNull() {
            when(dispatcher.dispatch(any(SubTask.class))).thenReturn(
                    A2aResponse.success("r1", "t1", AgentRole.MONITOR, "监控结果")
            );

            AgentExecutionResult result = supervisor.orchestrate("查询CPU指标", null);

            assertThat(result.success()).isTrue();
            assertThat(result.iterations()).isEqualTo(1);
            verify(dispatcher).dispatch(any(SubTask.class));
        }

        @Test
        @DisplayName("执行步骤包含角色名称和最终状态")
        void should_containRoleAndStatus_when_stepsGenerated() {
            when(dispatcher.dispatch(any(SubTask.class))).thenReturn(
                    A2aResponse.success("r1", "t1", AgentRole.MONITOR, "监控结果")
            );

            AgentExecutionResult result = supervisor.orchestrate("查询CPU指标", "conv-001");

            assertThat(result.steps()).hasSize(1);
            assertThat(result.steps().get(0)).contains("监控");
            assertThat(result.steps().get(0)).contains("SUCCESS");
            assertThat(result.steps().get(0)).contains("→");
        }

        @Test
        @DisplayName("编排模式为 PLAN_AND_SOLVE")
        void should_returnPlanAndSolveMode_when_orchestrationCompletes() {
            when(dispatcher.dispatch(any(SubTask.class))).thenReturn(
                    A2aResponse.success("r1", "t1", AgentRole.MONITOR, "监控结果")
            );

            AgentExecutionResult result = supervisor.orchestrate("查询CPU指标", "conv-001");

            assertThat(result.mode()).isEqualTo(AgentMode.PLAN_AND_SOLVE);
        }

        @Test
        @DisplayName("聚合结果包含原始用户输入")
        void should_containOriginalInput_when_resultAggregated() {
            String userInput = "监控CPU使用率";
            when(dispatcher.dispatch(any(SubTask.class))).thenReturn(
                    A2aResponse.success("r1", "t1", AgentRole.MONITOR, "监控结果")
            );

            AgentExecutionResult result = supervisor.orchestrate(userInput, "conv-001");

            assertThat(result.answer()).contains(userInput);
            assertThat(result.answer()).contains("Multi-Agent 协作结果");
        }

        @Test
        @DisplayName("conversationId 作为 parentTaskId 传入子任务")
        void should_useConversationIdAsParentTaskId_when_provided() {
            String conversationId = "conv-test-001";
            when(dispatcher.dispatch(any(SubTask.class))).thenReturn(
                    A2aResponse.success("r1", "t1", AgentRole.MONITOR, "监控结果")
            );

            supervisor.orchestrate("查询CPU指标", conversationId);

            verify(dispatcher).dispatch(argThat(task ->
                    conversationId.equals(task.parentTaskId())));
        }

        @Test
        @DisplayName("四领域全部分发时执行步骤包含四种角色")
        void should_containFourRoles_when_allDomainsTriggered() {
            when(dispatcher.dispatch(any(SubTask.class))).thenReturn(
                    A2aResponse.success("r1", "t1", AgentRole.MONITOR, "监控结果"),
                    A2aResponse.success("r2", "t2", AgentRole.ANALYZE, "分析结果"),
                    A2aResponse.success("r3", "t3", AgentRole.EXECUTE, "执行结果"),
                    A2aResponse.success("r4", "t4", AgentRole.KNOWLEDGE, "知识结果")
            );

            AgentExecutionResult result = supervisor.orchestrate(
                    "监控指标分析根因重启服务知识文档", "conv-001");

            assertThat(result.steps()).hasSize(4);
            assertThat(result.steps()).anyMatch(step -> step.contains("监控"));
            assertThat(result.steps()).anyMatch(step -> step.contains("分析"));
            assertThat(result.steps()).anyMatch(step -> step.contains("执行"));
            assertThat(result.steps()).anyMatch(step -> step.contains("知识"));
            assertThat(result.iterations()).isEqualTo(4);
        }

        @Test
        @DisplayName("子任务按优先级升序分发")
        void should_dispatchInPriorityOrder_when_multipleSubTasks() {
            when(dispatcher.dispatch(any(SubTask.class))).thenReturn(
                    A2aResponse.success("r", "t", AgentRole.MONITOR, "ok"));

            supervisor.orchestrate("监控指标分析根因重启服务知识文档", "conv-001");

            org.mockito.ArgumentCaptor<SubTask> captor =
                    org.mockito.ArgumentCaptor.forClass(SubTask.class);
            org.mockito.Mockito.verify(dispatcher, org.mockito.Mockito.times(4))
                    .dispatch(captor.capture());
            assertThat(captor.getAllValues())
                    .extracting(SubTask::priority)
                    .containsExactly(1, 2, 3, 4);
        }
    }

    @Nested
    @DisplayName("子任务超时")
    class SubTaskTimeout {

        @Test
        @DisplayName("子任务超时记为失败且不中断编排")
        void should_markFailedAndContinue_when_subTaskTimesOut() {
            SupervisorAgent shortTimeoutSupervisor =
                    new SupervisorAgent(dispatcher, registry, 1L);
            when(dispatcher.dispatch(any(SubTask.class))).thenAnswer(invocation -> {
                Thread.sleep(3000);
                return A2aResponse.success("r1", "t1", AgentRole.MONITOR, "监控结果");
            });

            AgentExecutionResult result = shortTimeoutSupervisor.orchestrate(
                    "查询CPU指标", "conv-timeout");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("超时");
            assertThat(result.steps()).allMatch(step -> step.contains("FAILED"));
        }

        @Test
        @DisplayName("超时时间非正数时构造抛出 IllegalArgumentException")
        void should_throwIllegalArg_when_timeoutNotPositive() {
            assertThatThrownBy(() -> new SupervisorAgent(dispatcher, registry, 0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("超时");
        }
    }
}
