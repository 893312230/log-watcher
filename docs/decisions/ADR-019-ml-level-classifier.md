# ADR-019：ML 日志定级层——正则漏判救援、只升不降、准确率门禁

## 背景

logwatch 告警管线（ADR-017）L1 定级层是固定词表正则
（ERROR/FATAL/SEVERE/Exception/WARN）。真实错误日志中大量条目不含这些字样——
"connection refused"、"timeout after 30s"、"OOM killed"、"permission denied"、
"no route to host" 等——L1 未命中即定级 INFO 并 SUPPRESS，形成**漏报**：
错误发生了，告警管线却当作普通日志丢弃。

词表可以无限扩充，但每加一个词就增加误报面，且无法覆盖未见过的表述。
需要一个能泛化的内容分类器做"正则漏判救援"。同时，这是项目第一个真正的
ML 组件，按 ADR-013 的原则（先基线后上线，砍掉无评测的伪 ML）必须自带
可复现的评测基线与上线门禁。

## 决策

1. **位置：L1 之后（新 L2），不是之前**。正则零成本先跑；ML 只处理正则
   未命中的事件。符合"便宜的资源先过滤"的成本模型，且 ML 推理（<1ms）
   只发生在候选子集上。层号重编：L0=0、L1=1、ML=2、RAG=3、LLM=4、
   Supervisor=5，`order()` 与 `markLayerReached` 同步调整。
2. **只升不降语义**：ML 层（`MlClassifyLayer`）只在
   `context.getLevel() == null`（L1 defer 模式放行待定）时介入；L1 已定级
   的事件绝不触碰。行为变化单调——相对旧版只多出"被救援的告警"，绝无回归。
3. **预过滤同步放开（mlPassthrough）**：`AlertPipelineService.onEvent` 原本
   要求关键字（ERROR/Exception/FATAL/OutOfMemory/StackOverflow/自定义）命中
   才入队——若维持不变，"connection refused"这类词表外事件在到达 L1 之前
   就被预过滤丢弃，ML 层将永远收不到它要救援的事件。故 `ml.enabled=true`
   时预过滤仅保留排除子串判定，全量日志经 L0 → L1 → L2 定级，
   判 INFO 即抑制（单条开销 <1ms，队列容量 2000 兜底背压）。
   `ml.enabled=false` 时预过滤行为与旧版逐字节一致。
   **连带调整一：异常检测门（StatisticalBaselineDetector）在直通模式下旁路**——
   其基线统计假设输入是经关键字预过滤的稀疏可疑流；直通后全量常规日志把
   间隔基线刷成短间隔均匀流，真实错误也被判"非异常"（生产冒烟实测：
   直通模式下连关键字 ERROR 探测线都被该门拦截）。
   **连带调整二：prod exclude-keywords 增补 `.pipeline.impl`**——落盘文件
   logger 按 39 字符缩写，缩写深度随类名长度变化：`pipeline.impl` 包下短类名
   （L1/L2/L3）渲染为 `c.s.a.logwatch.pipeline.*`，长类名（L0/L4/ML）渲染为
   `c.s.a.l.pipeline.*`，均不在原有 `c.s.agent.logwatch` 排除范围内；
   `.pipeline.impl` 是两种形态的共同子串。ML 救援日志与 L3 降级 WARN 均含
   ERROR/Exception 字样，不排除则每次救援/降级自产一条垃圾告警
   （生产实测 L3 自引用 6 条/27h）。
4. **L1 双模式**：`L1ClassifyLayer(boolean deferToMl)`。ml.enabled=true 时
   未命中 → `proceed()` 不设级别（交 ML 裁决）；否则维持旧行为
   `INFO + suppress`。ml.enabled=false 时全链路字节级同旧版。
5. **降级即旧行为**：分类器缺失 / 未就绪 / 推理异常 / 置信度低于阈值 /
   判为 INFO → 一律 `suppress()`，与旧版逐条一致。ML 层永远不会让一条
   本来会告警的日志被告警得更少。
6. **启动时训练 + 准确率门禁**：classpath 种子数据
   `ml/log-level-seed.tsv`（394 条三分类人工标注，每个故障族约 3 条释义变体）
   → 按类分层 80/20 切分 → Tribuo 逻辑回归（SGD/AdaGrad，50 epochs）训练
   → 留出集准确率 < `min-accuracy`（0.80）→ `isReady=false`，全层回退旧行为。
   当前种子数据留出集准确率 0.821，由 `TribuoLogLevelClassifierTest`
   在 CI 中强制复测（改坏种子数据 = 测试红）。支持 `training-data-path`
   外部 TSV 再训练——生产积累 `alert_record` 后导出重训，无需重新部署。
7. **Tribuo 进程内推理**（`org.tribuo:tribuo-classification-sgd:4.3.1`）：
   纯 Java 无 native 依赖，逻辑回归 + 归一化一元词袋特征（复用
   `LogEvent.normalizeDynamicParts` 抹平 IP/时间戳等动态片段）。
   2C/1.8G 生产容器内可忽略开销。
8. **可观测**：救援计数 Gauge `smartops.logwatch.ml.rescued`；启动日志
   打印训练样本数/留出集准确率/ready 状态；救援事件 INFO 日志含级别、
   置信度与指纹。
9. **存量 layerReached 语义漂移**：`alert_record` 历史数据的 2/3/4 旧语义
   （RAG/LLM/Supervisor）漂移为新语义（ML/RAG/LLM）。该列仅作诊断展示
   （前端 AlertView 纯透传），不参与任何判定逻辑，声明可接受，不做数据迁移。

## 备选方案

- **ONNX Runtime 推理**：模型表达力更强，但 native 库数十 MB，
  2C/1.8G 容器部署与升级负担大，且本项目无 GPU/重度模型诉求，放弃。
- **DJL（Deep Java Library）**：框架过重，同样拉 native 引擎，放弃。
- **远端推理服务**：内网延迟与可用性依赖不可接受（告警链路要求进程内确定性），放弃。
- **扩正则词表不加 ML**：误报面随词表线性膨胀，且无法泛化新表述，放弃
  （词表仍保留为 L1 第一道零成本闸）。
- **ML 层放 L1 之前**：每条日志都付 ML 推理成本，违背"便宜先过滤"，放弃。
- **维持关键字预过滤不变**：ML 层只能见到含内置/自定义关键字的事件，
  救援面退化为 OutOfMemoryError/StackOverflowError 等词边界个案，
  "connection refused"等词表外事件在 L1 之前就被丢弃——ML 层形同虚设，放弃
  （改为 ml.enabled 时放开预过滤，见决策 3）。
- **手写朴素贝叶斯（零依赖）**：约 150 行可实现，作为 Tribuo 依赖解析失败的
  降级预案记录；实际 Tribuo 引入顺利，未启用。

## 影响

- 新增配置键 `smartops.logwatch.ml.*`（`enabled` 默认 false，prod 默认 true；
  `confidence-threshold` 默认 0.85，prod 0.9——宁可少救不可错救）
- 新增领域端口 `LogLevelClassifier`（弃权降级契约：不抛异常，失败返回
  `ClassificationResult.abstain()`）与记录 `ClassificationResult`
- `AlertPipelineService.saveKnowledgeEntry` 阈值 `layerReached < 3` 调整为 `< 4`
  （语义不变：到达 LLM 层才沉淀知识）
- `ml.enabled=true` 时采集预过滤放开关键字要求（仅保留排除子串判定）——
  分析队列流量随全量日志上升，L0/L1/L2 单条开销 <1ms、队列容量与丢弃计数
  兜底；`LogKeywordMatcher` 新增 `isExcluded` 独立判定
- 行为变化单调可证：关闭开关 = 旧版；开启开关 = 旧版 + 被救援的告警
- 与 ADR-011 呼应：意图识别砍掉无评测的伪 ML 层，告警定级引入带评测门禁的
  真 ML 层——标准一致（可复现基线 + 不达标自动回退）
- 生产冒烟路径：往采集源写入 `Connection refused by db-01:3306`（正则不命中）
  → 断言告警产生且 `smartops.logwatch.ml.rescued ≥ 1`
