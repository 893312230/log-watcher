# ADR-016：RAG 两级检索落地——Spring AI ElasticsearchVectorStore + RRF + Markdown ETL

## 背景

阶段四任务9要求"BM25 + 向量两级检索 + RRF 融合 + 知识库 ETL"（ADR-013 已将三层检索降为两级、Rerank 降为可选）。`KnowledgeAgent` 当前以 `KNOWLEDGE_NOT_READY_PREFIX` 声明知识库未接入，是 RAG 的天然插接点。需决策：向量库实现路径、RRF 融合位置、ETL 形态、集成测试策略。

## 决策

1. **向量库 = Spring AI `ElasticsearchVectorStore`**（用户决策 2026-07-22）。2.0.0 已验证存在（`Builder(Rest5Client, EmbeddingModel)`）。ES 8.x 单节点入 docker-compose（`xpack.security.enabled=false`，512m 堆）。
2. **RRF 融合**：优先使用 starter 的 hybrid 检索能力；若 2.0.0 该能力不满足（实现时验证），回退为两路查询（`similaritySearch` 向量路 + BM25 文本路）在客户端按 `1/(k+rank)` 融合（k=60 可配）。端口 `KnowledgeRetriever` 放 smart-ops-domain，实现 `EsKnowledgeRetriever` 放 infrastructure `knowledge/impl/`，**接口契约：失败返回空表不抛异常**，支撑 KnowledgeAgent 降级。
3. **ETL = ApplicationRunner + 配置开关**：`smartops.knowledge.etl.enabled=false` 默认关；扫描 `source-dir`（默认 `docs/knowledge`）的 `*.md` → 标题感知切分（`MarkdownChunker`，chunk-size 兜底）→ embed → `VectorStore.add` 批量写入；幂等 `_id = hash(source + chunkIndex)`；启动时校验 embedding 维度与配置 `dimensions` 一致，不一致快速失败。
4. **集成测试策略**：不引入 Testcontainers。全部逻辑纯 Mockito（mock `VectorStore`/`EmbeddingModel`/`ElasticsearchClient`）保证 JaCoCo 95/90 门；真实 ES/Redis 验证用极薄 IT 类 `@EnabledIfEnvironmentVariable(named="SMARTOPS_IT", matches="true")`，默认构建不运行（本机 Docker daemon 当前不可用）。

## 备选方案

- **原生 elasticsearch-java client 写 BM25+knn+`rank:{rrf:{}}`**：RRF 完全可控，但失去 starter 抽象且需手写 mapping/序列化；作为 starter hybrid 不足时的回退路径保留。
- **PGVector 替代 ES**：ADR-013 已决策保留 ES 首选（团队经验），PGVector 仍为备选。
- **ETL 做成 REST 端点或启动强制执行**：REST 端点阶段四无管理面需求；启动强制执行会让无知识库环境启动失败，均放弃。
- **引入 Testcontainers 跑 IT**：Docker daemon 当前不可用且拖慢 `mvn verify`，放弃。

## 影响

- 新增配置键 `smartops.elasticsearch.{enabled,uris,index,rrf-k,top-k}`、`smartops.knowledge.etl.{enabled,source-dir,chunk-size}`（application.yml 带中文注释），默认全关 → 无 ES 环境应用照常启动
- `KnowledgeAgent` 经 `ObjectProvider<KnowledgeRetriever>` 注入：无 Bean/空结果/异常 → 保留 `KNOWLEDGE_NOT_READY_PREFIX` 降级；命中 → 注入 context 段
- ETL 真实运行依赖 ADR-015 的 Ollama bge-m3 与 docker-compose 的 ES
- 测试：`EsKnowledgeRetrieverTest`（两路融合/单路空/全空/异常降级/topK 截断）、ETL 三类单测、门控薄 IT
