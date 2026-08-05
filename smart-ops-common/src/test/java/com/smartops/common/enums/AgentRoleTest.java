package com.smartops.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentRole} 枚举测试。
 *
 * <p>验证 Agent 角色枚举的完整性、显示名/描述获取及 Worker 判定契约。
 * 对应 agent.md 阶段三 Multi-Agent 架构角色划分。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AgentRoleTest {

    @Nested
    @DisplayName("枚举完整性")
    class EnumCompleteness {

        @Test
        @DisplayName("枚举包含全部 5 个角色")
        void should_containAllRoles_when_valuesCalled() {
            AgentRole[] roles = AgentRole.values();

            assertThat(roles).hasSize(5);
            assertThat(roles).containsExactly(
                    AgentRole.SUPERVISOR,
                    AgentRole.MONITOR,
                    AgentRole.ANALYZE,
                    AgentRole.EXECUTE,
                    AgentRole.KNOWLEDGE
            );
        }

        @Test
        @DisplayName("valueOf 按名称正确返回枚举常量")
        void should_returnCorrectRole_when_valueOfCalled() {
            assertThat(AgentRole.valueOf("SUPERVISOR")).isEqualTo(AgentRole.SUPERVISOR);
            assertThat(AgentRole.valueOf("MONITOR")).isEqualTo(AgentRole.MONITOR);
            assertThat(AgentRole.valueOf("ANALYZE")).isEqualTo(AgentRole.ANALYZE);
            assertThat(AgentRole.valueOf("EXECUTE")).isEqualTo(AgentRole.EXECUTE);
            assertThat(AgentRole.valueOf("KNOWLEDGE")).isEqualTo(AgentRole.KNOWLEDGE);
        }

        @Test
        @DisplayName("枚举 ordinal 按声明顺序递增")
        void should_haveAscendingOrdinal_when_inDeclarationOrder() {
            assertThat(AgentRole.SUPERVISOR.ordinal()).isLessThan(AgentRole.MONITOR.ordinal());
            assertThat(AgentRole.MONITOR.ordinal()).isLessThan(AgentRole.ANALYZE.ordinal());
            assertThat(AgentRole.ANALYZE.ordinal()).isLessThan(AgentRole.EXECUTE.ordinal());
            assertThat(AgentRole.EXECUTE.ordinal()).isLessThan(AgentRole.KNOWLEDGE.ordinal());
        }

        @Test
        @DisplayName("toString 返回枚举常量名")
        void should_returnConstantName_when_toStringCalled() {
            assertThat(AgentRole.SUPERVISOR.toString()).isEqualTo("SUPERVISOR");
            assertThat(AgentRole.MONITOR.toString()).isEqualTo("MONITOR");
            assertThat(AgentRole.ANALYZE.toString()).isEqualTo("ANALYZE");
            assertThat(AgentRole.EXECUTE.toString()).isEqualTo("EXECUTE");
            assertThat(AgentRole.KNOWLEDGE.toString()).isEqualTo("KNOWLEDGE");
        }
    }

    @Nested
    @DisplayName("显示名与描述")
    class DisplayNameAndDescription {

        @Test
        @DisplayName("SUPERVISOR 的显示名与描述正确")
        void should_returnCorrectDisplayName_when_supervisorAccessed() {
            assertThat(AgentRole.SUPERVISOR.getDisplayName()).isEqualTo("主管");
            assertThat(AgentRole.SUPERVISOR.getDescription())
                    .isEqualTo("负责任务分解、Worker 分配与结果聚合");
        }

        @Test
        @DisplayName("MONITOR 的显示名与描述正确")
        void should_returnCorrectDisplayName_when_monitorAccessed() {
            assertThat(AgentRole.MONITOR.getDisplayName()).isEqualTo("监控");
            assertThat(AgentRole.MONITOR.getDescription())
                    .isEqualTo("实时监控、告警查询、指标趋势分析");
        }

        @Test
        @DisplayName("ANALYZE 的显示名与描述正确")
        void should_returnCorrectDisplayName_when_analyzeAccessed() {
            assertThat(AgentRole.ANALYZE.getDisplayName()).isEqualTo("分析");
            assertThat(AgentRole.ANALYZE.getDescription())
                    .isEqualTo("根因分析、日志分析、异常检测");
        }

        @Test
        @DisplayName("EXECUTE 的显示名与描述正确")
        void should_returnCorrectDisplayName_when_executeAccessed() {
            assertThat(AgentRole.EXECUTE.getDisplayName()).isEqualTo("执行");
            assertThat(AgentRole.EXECUTE.getDescription())
                    .isEqualTo("自动化运维操作（重启、扩缩容、配置变更）");
        }

        @Test
        @DisplayName("KNOWLEDGE 的显示名与描述正确")
        void should_returnCorrectDisplayName_when_knowledgeAccessed() {
            assertThat(AgentRole.KNOWLEDGE.getDisplayName()).isEqualTo("知识");
            assertThat(AgentRole.KNOWLEDGE.getDescription())
                    .isEqualTo("运维知识库问答、最佳实践推荐");
        }

        @ParameterizedTest
        @EnumSource(AgentRole.class)
        @DisplayName("所有角色的显示名与描述均非空")
        void should_haveNonBlankDisplayName_when_anyRole(AgentRole role) {
            assertThat(role.getDisplayName()).isNotBlank();
            assertThat(role.getDescription()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("isWorker 方法")
    class IsWorker {

        @Test
        @DisplayName("SUPERVISOR 角色判定为非 Worker")
        void should_returnFalse_when_roleIsSupervisor() {
            assertThat(AgentRole.SUPERVISOR.isWorker()).isFalse();
        }

        @Test
        @DisplayName("MONITOR 角色判定为 Worker")
        void should_returnTrue_when_roleIsMonitor() {
            assertThat(AgentRole.MONITOR.isWorker()).isTrue();
        }

        @Test
        @DisplayName("ANALYZE 角色判定为 Worker")
        void should_returnTrue_when_roleIsAnalyze() {
            assertThat(AgentRole.ANALYZE.isWorker()).isTrue();
        }

        @Test
        @DisplayName("EXECUTE 角色判定为 Worker")
        void should_returnTrue_when_roleIsExecute() {
            assertThat(AgentRole.EXECUTE.isWorker()).isTrue();
        }

        @Test
        @DisplayName("KNOWLEDGE 角色判定为 Worker")
        void should_returnTrue_when_roleIsKnowledge() {
            assertThat(AgentRole.KNOWLEDGE.isWorker()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "SUPERVISOR")
        @DisplayName("除 SUPERVISOR 外的角色均判定为 Worker")
        void should_returnTrue_when_roleIsNotSupervisor(AgentRole role) {
            assertThat(role.isWorker()).isTrue();
            assertThat(role).isNotEqualTo(AgentRole.SUPERVISOR);
        }
    }
}
