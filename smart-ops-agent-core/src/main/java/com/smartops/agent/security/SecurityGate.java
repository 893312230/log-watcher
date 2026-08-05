package com.smartops.agent.security;

import com.smartops.common.exception.SecurityViolationException;
import com.smartops.infrastructure.observability.Observability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 最小安全门（L3 人工确认的提前落地）。
 *
 * <p>对应规划调整：安全控制从阶段五提前，消除 ExecuteAgent 具备
 * 重启/扩缩容能力却无任何防护的执行窗口（P0-3 缺陷）。</p>
 *
 * <p><b>工作方式</b>：
 * <ol>
 *   <li>{@link #assessRisk} 按关键词将操作文本分为 HIGH / LOW 两档风险</li>
 *   <li>Worker 执行高危操作前调用 {@link #checkPermitted}：
 *       当前线程未被标记为"已确认"（{@link ConfirmationContext}）时，
 *       抛出 {@link SecurityViolationException}（错误码 SECURITY_CONFIRM_REQUIRED）</li>
 *   <li>API 层捕获该异常，签发一次性确认令牌；客户端携带令牌重提后，
 *       门放行（令牌验证与消费见 {@code ConfirmationTokenStore} + AgentController）</li>
 * </ol></p>
 *
 * <p>只读操作（查询、分析、知识问答）恒为 LOW，直接放行。
 * 本门是高危操作的兜底防线，不替代阶段五的完整四级安全模型（L0-L3）。</p>
 *
 * <p>线程安全：无内部状态；确认标记经 {@link ConfirmationContext} 按线程隔离。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class SecurityGate {

    private static final Logger log = LoggerFactory.getLogger(SecurityGate.class);

    /** 确认令牌缺失时的安全违规错误码。 */
    public static final String ERROR_CODE_CONFIRM_REQUIRED =
            SecurityViolationException.ERROR_CODE_PREFIX + "CONFIRM_REQUIRED";

    /** 高危操作关键词（变更系统状态的动作）。 */
    private static final String[] HIGH_RISK_KEYWORDS = {
            "重启", "restart", "扩缩容", "扩容", "缩容", "scale",
            "配置变更", "变更配置", "删除", "delete", "部署", "deploy",
            "停机", "下线", "回滚", "rollback", "迁移", "migrate"
    };

    /**
     * 操作风险分级。
     */
    public enum OperationRisk {
        /** 高危：变更系统状态，需人工确认。 */
        HIGH,
        /** 低危：只读操作，直接放行。 */
        LOW
    }

    /** 可观测性门面（安全决策指标+审计），可为 null（测试或裁剪场景）。 */
    private final Observability observability;

    /**
     * 构造安全门（无可观测性，供测试使用）。
     */
    public SecurityGate() {
        this(null);
    }

    /**
     * 构造安全门。
     *
     * <p>类内存在多个构造器，须以 {@link Autowired} 显式标注注入入口。</p>
     *
     * @param observability 可观测性门面（安全决策指标+审计），可为 null
     */
    @Autowired
    public SecurityGate(Observability observability) {
        this.observability = observability;
    }

    /**
     * 评估操作文本的风险等级。
     *
     * @param operationText 操作描述文本，可为 null（视为 LOW）
     * @return 风险等级
     */
    public OperationRisk assessRisk(String operationText) {
        if (operationText == null || operationText.isBlank()) {
            return OperationRisk.LOW;
        }
        String lower = operationText.toLowerCase(Locale.ROOT);
        for (String keyword : HIGH_RISK_KEYWORDS) {
            if (lower.contains(keyword)) {
                return OperationRisk.HIGH;
            }
        }
        return OperationRisk.LOW;
    }

    /**
     * 校验操作是否允许执行。
     *
     * <p>LOW 风险直接放行；HIGH 风险要求当前线程已被标记为"已确认"
     * （即客户端已携带有效一次性确认令牌重提请求），否则抛出安全违规异常。</p>
     *
     * @param operationText 操作描述文本
     * @throws SecurityViolationException 当操作高危且未经人工确认时
     */
    public void checkPermitted(String operationText) {
        if (assessRisk(operationText) == OperationRisk.LOW) {
            return;
        }
        if (ConfirmationContext.isConfirmed()) {
            log.info("高危操作已通过人工确认，放行: operation={}", abbreviate(operationText));
            observeSecurityDecision(operationText, true);
            return;
        }
        log.warn("高危操作缺少人工确认，拒绝执行: operation={}", abbreviate(operationText));
        observeSecurityDecision(operationText, false);
        throw new SecurityViolationException(ERROR_CODE_CONFIRM_REQUIRED,
                "高风险操作需要人工确认: " + abbreviate(operationText));
    }

    /**
     * 安全决策观测（指标+审计）：observability 缺失时静默跳过。
     * 仅记录高危操作的放行/拦截决策，低危只读操作不记录以避免噪音。
     */
    private void observeSecurityDecision(String operationText, boolean permitted) {
        if (observability != null) {
            observability.recordSecurityDecision(abbreviate(operationText), permitted);
        }
    }

    /**
     * 截断操作文本，避免日志与异常消息泄露完整指令。
     *
     * @param text 原始文本
     * @return 截断后的文本（最长 50 字符）
     */
    private String abbreviate(String text) {
        return text.length() <= 50 ? text : text.substring(0, 50) + "...";
    }
}
