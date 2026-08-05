package com.smartops.agent.logwatch.pipeline.impl;

import com.smartops.agent.logwatch.pipeline.AnalysisLayer;
import com.smartops.domain.knowledge.KnowledgeChunk;
import com.smartops.domain.knowledge.KnowledgeRetriever;
import com.smartops.domain.logwatch.AnalysisContext;
import com.smartops.domain.logwatch.AnalysisOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * L3 知识库 RAG 参考层。
 *
 * <p>以日志内容检索运维知识库，命中条目（runbook 来源路径）累积进
 * {@link AnalysisContext#getKnowledgeRefs()}，供 L4 LLM 分析时引用。
 * 本层永不终止管线：</p>
 * <ul>
 *   <li>检索器为 null（ES 未开启）→ 直接放行，即"知识库未接入"降级</li>
 *   <li>检索异常（违反端口降级契约的实现）→ 捕获并放行</li>
 *   <li>命中与否都放行，是否足够定位交由 L4 判断</li>
 * </ul>
 *
 * <p>线程安全：检索器实现须线程安全（EsKnowledgeRetriever 满足）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class L2RagLayer implements AnalysisLayer {

    private static final Logger log = LoggerFactory.getLogger(L2RagLayer.class);

    /** 每次检索的最大命中条数（控制注入 L4 的上下文体积）。 */
    private static final int TOP_K = 3;

    /** 检索查询的最大字符数（长堆栈截断，控制 embedding/token 成本）。 */
    private static final int QUERY_MAX_LENGTH = 500;

    private final KnowledgeRetriever retriever;

    /**
     * 构造 L3 知识库参考层。
     *
     * @param retriever 知识库检索器，可为 null（表示知识库未接入，本层空转）
     */
    public L2RagLayer(KnowledgeRetriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public int order() {
        return 3;
    }

    @Override
    public AnalysisOutcome apply(AnalysisContext context) {
        context.markLayerReached(3);
        if (retriever == null) {
            return AnalysisOutcome.proceed();
        }
        try {
            String query = abbreviate(context.getEvent().content());
            List<KnowledgeChunk> hits = retriever.retrieve(query, TOP_K);
            for (KnowledgeChunk hit : hits) {
                context.addKnowledgeRef(hit.source());
            }
        } catch (RuntimeException e) {
            log.warn("知识库检索失败，降级放行: {}", e.toString());
        }
        return AnalysisOutcome.proceed();
    }

    /**
     * 截断超长日志内容作为检索查询。
     *
     * @param content 日志内容
     * @return 不超过 {@link #QUERY_MAX_LENGTH} 的查询文本
     */
    private String abbreviate(String content) {
        return content.length() <= QUERY_MAX_LENGTH
                ? content
                : content.substring(0, QUERY_MAX_LENGTH);
    }
}
