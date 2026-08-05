package com.smartops.agent.worker;

import com.smartops.agent.a2a.AgentCardRegistry;
import com.smartops.common.enums.AgentRole;
import com.smartops.common.enums.IntentType;
import com.smartops.common.model.A2aRequest;
import com.smartops.common.model.A2aResponse;
import com.smartops.common.model.AgentCard;
import com.smartops.domain.knowledge.KnowledgeChunk;
import com.smartops.domain.knowledge.KnowledgeRetriever;
import com.smartops.infrastructure.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 知识 Agent。
 *
 * <p>阶段三 Worker Agent 之一，负责运维知识库问答、最佳实践推荐。
 * 阶段四起接入两级混合检索 RAG（ADR-016）：{@link KnowledgeRetriever} Bean 可用
 * 且检索命中时，将检索结果作为上下文注入系统提示词并直接回答；
 * Bean 缺失、检索为空或检索异常时降级为纯 LLM 回答并附"知识库未接入"声明前缀。</p>
 *
 * <p>支持的意图：{@link IntentType#KNOWLEDGE_QA}。</p>
 *
 * <p>线程安全：依赖组件线程安全，本组件无状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class KnowledgeAgent extends AbstractWorkerAgent {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAgent.class);

    /** 角色专属系统提示词（外置，类加载时加载一次）。 */
    private static final String KNOWLEDGE_PROMPT = loadPromptTemplate("prompts/worker-knowledge.txt");

    /** 知识库未接入声明前缀：检索不可用或无命中时，告知用户回答基于模型通用知识。 */
    static final String KNOWLEDGE_NOT_READY_PREFIX =
            "【说明】运维知识库尚未接入（阶段四将引入 RAG 检索），以下回答基于模型通用知识：\n";

    /** RAG 上下文块标题：检索命中时附加在系统提示词之后。 */
    static final String CONTEXT_BLOCK_HEADER =
            "\n\n【知识库检索结果】（以下内容来自运维知识库，优先依据其回答并注明来源；与问题无关时忽略）：\n";

    /** 知识库检索器（ES + embedding 双开关任一关闭时不存在）。 */
    private final KnowledgeRetriever knowledgeRetriever;

    /** 默认召回条数（smartops.elasticsearch.top-k）。 */
    private final int topK;

    /**
     * 构造知识 Agent。
     *
     * @param registry          Agent Card 注册中心
     * @param chatService       LLM 对话服务
     * @param retrieverProvider 知识库检索器提供者（Bean 缺失时降级）
     * @param topK              检索召回条数
     */
    public KnowledgeAgent(AgentCardRegistry registry, ChatService chatService,
                          ObjectProvider<KnowledgeRetriever> retrieverProvider,
                          @Value("${smartops.elasticsearch.top-k:5}") int topK) {
        super(buildCard(), registry, chatService);
        this.knowledgeRetriever = retrieverProvider.getIfAvailable();
        this.topK = topK;
    }

    /**
     * 构建知识 Agent 的能力卡片。
     */
    private static AgentCard buildCard() {
        return new AgentCard(
                "knowledge-agent",
                AgentRole.KNOWLEDGE,
                "知识Agent",
                "运维知识库问答、最佳实践推荐",
                Set.of("knowledge-base", "best-practices", "documentation", "faq"),
                Set.of(IntentType.KNOWLEDGE_QA),
                5
        );
    }

    @Override
    protected A2aResponse doHandle(A2aRequest request) {
        log.info("知识 Agent 处理指令: taskId={}, instruction={}",
                request.taskId(), request.instruction());

        List<KnowledgeChunk> chunks = retrieve(request.instruction());
        if (chunks.isEmpty()) {
            // 降级路径：无检索器/无命中/检索异常 → 纯 LLM 回答 + 未接入声明前缀
            String answer = chatWithRolePrompt(KNOWLEDGE_PROMPT, request);
            return A2aResponse.success(request.requestId(), request.taskId(),
                    AgentRole.KNOWLEDGE, KNOWLEDGE_NOT_READY_PREFIX + answer);
        }

        // RAG 路径：检索结果作为上下文注入系统提示词
        String ragPrompt = KNOWLEDGE_PROMPT + buildContextBlock(chunks);
        String answer = chatWithRolePrompt(ragPrompt, request);
        return A2aResponse.success(request.requestId(), request.taskId(),
                AgentRole.KNOWLEDGE, answer);
    }

    /**
     * 安全执行检索：检索器缺失或抛异常时返回空表（走降级路径）。
     *
     * @param query 查询文本
     * @return 命中块列表，不可用时为空表
     */
    private List<KnowledgeChunk> retrieve(String query) {
        if (knowledgeRetriever == null) {
            return List.of();
        }
        try {
            return knowledgeRetriever.retrieve(query, topK);
        } catch (Exception e) {
            // KnowledgeRetriever 契约为实现不抛异常；此处兜底防御，保证降级语义不被破坏
            log.warn("知识库检索异常，降级为纯 LLM 回答: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将检索块渲染为 RAG 上下文块。
     *
     * @param chunks 检索命中块
     * @return 附在系统提示词后的上下文文本
     */
    private String buildContextBlock(List<KnowledgeChunk> chunks) {
        StringBuilder block = new StringBuilder(CONTEXT_BLOCK_HEADER);
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            block.append('[').append(i + 1).append("] 来源：").append(chunk.source());
            if (!chunk.title().isEmpty()) {
                block.append('（').append(chunk.title()).append('）');
            }
            block.append('\n').append(chunk.content()).append('\n');
        }
        return block.toString();
    }
}
