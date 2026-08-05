package com.smartops.agent.logwatch;

import com.smartops.domain.topology.TopologyNode;
import com.smartops.domain.topology.port.TopologyRepository;
import com.smartops.domain.logwatch.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 拓扑感知 RCA 增强器（阶段八）。
 *
 * <p>告警时查询拓扑图，找到受影响服务的上下游依赖，
 * 构造级联故障上下文注入 LLM 分析 prompt。</p>
 */
public class TopologyEnricher {

    private static final Logger log = LoggerFactory.getLogger(TopologyEnricher.class);
    private final TopologyRepository topologyRepo;

    public TopologyEnricher(TopologyRepository topologyRepo) {
        this.topologyRepo = topologyRepo;
    }

    /**
     * 为告警事件查找关联的拓扑节点与上下游。
     *
     * @param sourceName 告警来源路径（匹配节点名/logPath）
     * @return 拓扑上下文文本，可直接注入 LLM prompt
     */
    public String enrich(String sourceName) {
        try {
            var nodes = topologyRepo.findAllNodes();
            var edges = topologyRepo.findAllEdges();
            // 查找匹配节点
            TopologyNode matched = nodes.stream()
                    .filter(n -> sourceName.contains(n.name()) || sourceName.contains(n.host()))
                    .max(java.util.Comparator.comparingInt(n -> n.name().length())).orElse(null);
            if (matched == null) return "";

            // 找上游和下游
            var upstream = edges.stream()
                    .filter(e -> e.targetId().equals(matched.id()))
                    .map(e -> nodes.stream().filter(n -> n.id().equals(e.sourceId())).findFirst())
                    .filter(java.util.Optional::isPresent).map(java.util.Optional::get)
                    .toList();
            var downstream = edges.stream()
                    .filter(e -> e.sourceId().equals(matched.id()))
                    .map(e -> nodes.stream().filter(n -> n.id().equals(e.targetId())).findFirst())
                    .filter(java.util.Optional::isPresent).map(java.util.Optional::get)
                    .toList();

            StringBuilder sb = new StringBuilder();
            sb.append("\n【拓扑上下文】\n");
            sb.append("受影响服务: ").append(matched.name()).append(" (").append(matched.status()).append(")\n");
            if (!upstream.isEmpty()) {
                sb.append("上游依赖方: ");
                upstream.forEach(u -> sb.append(u.name()).append("(").append(u.status()).append(") "));
                sb.append("\n");
            }
            if (!downstream.isEmpty()) {
                sb.append("下游被依赖方: ");
                downstream.forEach(d -> sb.append(d.name()).append("(").append(d.status()).append(") "));
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("拓扑增强失败: {}", e.toString());
            return "";
        }
    }
}
