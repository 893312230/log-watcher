package com.smartops.agent.intent;

import com.smartops.common.enums.IntentType;
import com.smartops.common.model.IntentResult;
import com.smartops.infrastructure.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L4 LLM 意图识别器（最终兜底）。
 *
 * <p>对应 agent.md 阶段二任务4。当 L1 正则、L2 词频均无法确定意图时，
 * 调用 LLM 进行语义级意图分类。LLM 语义理解能力最强，但成本最高，
 * 因此作为最后一道兜底防线。</p>
 *
 * <p><b>提示词外置</b>：分类提示词维护在 classpath:prompts/intent-l4.txt，
 * 要求 LLM 以 JSON 输出 {"intent":"...","confidence":0..1}。
 * 与内嵌字符串相比，外置便于独立评审与调优，且遵循 agent.md 的
 * Prompt 头部注释规范（用途、输入变量、期望输出）。</p>
 *
 * <p><b>调用通路修复（阶段五）</b>：本类原直接调用 ChatClient
 * 绕过了 ChatService 的监控/审计漏斗（LLM_CALL 指标与审计事件缺失）
 * 且未设置 conversationId 致 MessageChatMemoryAdvisor 校验失败。
 * 现改用 {@link ChatService#chatWithSystemPrompt}——系统指令
 * 走 system 角色，用户输入走 user 角色，自动经过 Observability。<b>
 *
 * <p><b>置信度解析</b>：置信度取自 LLM 返回的 JSON confidence 字段（真实值），
 * 解析失败回退 0.5；无法解析 JSON 时降级为关键词匹配（置信度 0.4）；
 * LLM 调用异常时返回 UNKNOWN（置信度 0.1），确保 Pipeline 不中断。</p>
 *
 * <p>线程安全：ChatService 线程安全，提示词加载后不可变，组件单例，线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class L4LLMRecognizer implements IntentRecognizer {

    private static final Logger log = LoggerFactory.getLogger(L4LLMRecognizer.class);

    /** 意图类型正则：从 LLM JSON 输出中提取 intent 字段。 */
    private static final Pattern INTENT_PATTERN = Pattern.compile(
            "\"intent\"\\s*:\\s*\"(\\w+)\"");

    /**
     * 置信度正则：从 LLM JSON 输出中提取 confidence 字段。
     * 通过前后边界断言只接受 0.0-1.0 范围内的数值，
     * 越界值（如 1.5）不匹配，落入解析失败回退路径。
     */
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile(
            "\"confidence\"\\s*:\\s*(?<![\\d.])(0?\\.\\d+|1\\.0+|[01])(?![\\d.])");

    /** JSON 置信度字段解析失败时的回退置信度。 */
    private static final double CONFIDENCE_PARSE_FALLBACK = 0.5;

    /** 关键词匹配兜底置信度（解析回退 0.5 × 0.8 折扣，反映降级的不确定性）。 */
    private static final double KEYWORD_FALLBACK_CONFIDENCE = CONFIDENCE_PARSE_FALLBACK * 0.8;

    /** LLM 调用失败时的置信度。 */
    private static final double LLM_FAILURE_CONFIDENCE = 0.1;

    /** 模板中系统指令与用户输入占位符的分隔标记（加载时切分 system/user）。 */
    private static final String USER_INPUT_MARKER = "用户输入:";

    /** LLM 对话服务（漏斗式观测+审计，无记忆元调用路径）。 */
    private final ChatService chatService;

    /** 意图分类系统提示词（加载时从模板中提取，不含用户输入占位符）。 */
    private final String systemPrompt;

    /**
     * 构造 L4 识别器，加载外置提示词模板并切分 system/user。
     *
     * @param chatService LLM 对话服务，用于调用 LLM 并自动记录审计
     * @throws IllegalStateException 当提示词资源无法读取时
     */
    public L4LLMRecognizer(ChatService chatService) {
        this.chatService = chatService;
        try {
            String template = new ClassPathResource("prompts/intent-l4.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            int markerIndex = template.indexOf(USER_INPUT_MARKER);
            if (markerIndex < 0) {
                throw new IllegalStateException(
                        "L4 提示词缺少 '" + USER_INPUT_MARKER + "' 标记");
            }
            this.systemPrompt = template.substring(0, markerIndex).trim();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "无法加载 L4 意图识别提示词: prompts/intent-l4.txt", e);
        }
    }

    @Override
    public IntentResult recognize(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为 null 或空白");
        }

        try {
            String llmResponse = chatService.chatWithSystemPrompt(systemPrompt, userInput);
            log.debug("L4 LLM 原始响应: {}", llmResponse);
            return parseLLMResponse(llmResponse, userInput);
        } catch (Exception e) {
            log.error("L4 LLM 调用失败", e);
            return new IntentResult(IntentType.UNKNOWN, LLM_FAILURE_CONFIDENCE, getLayer(), null);
        }
    }

    @Override
    public String getLayer() {
        return IntentResult.SOURCE_L4_LLM;
    }

    /**
     * 解析 LLM 返回的 JSON 格式意图与置信度。
     *
     * <p>意图类型缺失时降级为关键词匹配；意图类型有效但置信度字段
     * 缺失或越界时回退 0.5。</p>
     *
     * @param response  LLM 原始响应（可为 null）
     * @param userInput 原始用户输入（用于关键词兜底）
     * @return 解析后的意图结果
     */
    IntentResult parseLLMResponse(String response, String userInput) {
        if (response == null || response.isBlank()) {
            log.warn("L4 LLM 返回空响应，降级为关键词匹配");
            return fallbackKeywordMatch(userInput);
        }

        Matcher intentMatcher = INTENT_PATTERN.matcher(response);
        if (intentMatcher.find()) {
            String intentName = intentMatcher.group(1);
            try {
                IntentType intentType = IntentType.valueOf(intentName);
                return new IntentResult(intentType, parseConfidence(response), getLayer(), null);
            } catch (IllegalArgumentException e) {
                log.warn("LLM 返回了未知的意图类型: {}", intentName);
                return fallbackKeywordMatch(userInput);
            }
        }

        log.warn("无法从 LLM 响应中解析意图类型: {}", response);
        return fallbackKeywordMatch(userInput);
    }

    /**
     * 从 LLM JSON 响应中解析真实置信度，解析失败回退 0.5。
     *
     * @param response LLM 原始响应
     * @return 0.0-1.0 的置信度
     */
    private double parseConfidence(String response) {
        Matcher confidenceMatcher = CONFIDENCE_PATTERN.matcher(response);
        if (confidenceMatcher.find()) {
            return Double.parseDouble(confidenceMatcher.group(1));
        }
        log.warn("无法从 LLM 响应中解析置信度，回退 {}: {}", CONFIDENCE_PARSE_FALLBACK, response);
        return CONFIDENCE_PARSE_FALLBACK;
    }

    /**
     * 关键词匹配兜底：当 LLM 输出无法解析时，基于简单关键词匹配意图。
     *
     * @param userInput 用户输入
     * @return 匹配的意图结果
     */
    private IntentResult fallbackKeywordMatch(String userInput) {
        Map<IntentType, String[]> keywordMap = Map.of(
                IntentType.QUERY_METRIC, new String[]{"CPU", "内存", "磁盘", "QPS", "使用率"},
                IntentType.ANALYZE_ALERT, new String[]{"告警", "报警", "异常", "故障"},
                IntentType.ROOT_CAUSE, new String[]{"为什么", "原因", "根因"},
                IntentType.EXECUTE_OPERATION, new String[]{"重启", "扩容", "缩容", "部署"},
                IntentType.KNOWLEDGE_QA, new String[]{"如何", "怎么", "怎样"}
        );

        for (Map.Entry<IntentType, String[]> entry : keywordMap.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (userInput.contains(keyword)) {
                    return new IntentResult(entry.getKey(), KEYWORD_FALLBACK_CONFIDENCE,
                            getLayer(), null);
                }
            }
        }

        return new IntentResult(IntentType.UNKNOWN, LLM_FAILURE_CONFIDENCE, getLayer(), null);
    }
}
