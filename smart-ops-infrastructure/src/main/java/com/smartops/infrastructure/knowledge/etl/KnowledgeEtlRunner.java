package com.smartops.infrastructure.knowledge.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 知识库 ETL 启动器（阶段四，ADR-016）。
 *
 * <p>{@code smartops.knowledge.etl.enabled=true} 时随应用启动执行：
 * 扫描 source-dir 下全部 {@code *.md} 文件（字典序），逐个切分并写入向量库。
 * 幂等：重复执行以相同 id 覆盖写（{@link KnowledgeIndexer#stableId}）。</p>
 *
 * <p>source-dir 不存在时记录 warn 并跳过（不阻断启动）；
 * 读文件/索引失败（含 embedding 维度不匹配）则异常上抛使启动快速失败。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "smartops.knowledge.etl.enabled", havingValue = "true")
@ConditionalOnBean(KnowledgeIndexer.class)
public class KnowledgeEtlRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEtlRunner.class);

    private final MarkdownChunker chunker;
    private final KnowledgeIndexer indexer;
    private final Path sourceDir;

    /**
     * 构造 ETL 启动器。
     *
     * @param chunker   Markdown 切分器
     * @param indexer   知识库索引器
     * @param sourceDir 知识源目录（smartops.knowledge.etl.source-dir）
     */
    public KnowledgeEtlRunner(MarkdownChunker chunker, KnowledgeIndexer indexer,
                              @Value("${smartops.knowledge.etl.source-dir:docs/knowledge}") String sourceDir) {
        this.chunker = Objects.requireNonNull(chunker, "chunker 不能为 null");
        this.indexer = Objects.requireNonNull(indexer, "indexer 不能为 null");
        this.sourceDir = Path.of(sourceDir);
    }

    /**
     * 执行 ETL：扫描 → 切分 → 索引。
     *
     * @param args 启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!Files.isDirectory(sourceDir)) {
            log.warn("知识库 ETL 跳过：source-dir 不存在（{}）", sourceDir.toAbsolutePath());
            return;
        }
        List<Path> markdownFiles = scanMarkdownFiles();
        int totalChunks = 0;
        for (Path file : markdownFiles) {
            String source = sourceDir.relativize(file).toString();
            totalChunks += indexer.index(source, chunker.chunk(readFile(file)));
        }
        log.info("知识库 ETL 完成：files={}, chunks={}", markdownFiles.size(), totalChunks);
    }

    /**
     * 扫描 source-dir 下全部 Markdown 文件（字典序，保证幂等顺序）。
     *
     * @return .md 文件列表
     */
    private List<Path> scanMarkdownFiles() {
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("知识库 ETL 扫描失败: " + sourceDir, e);
        }
    }

    /**
     * 读取文件全文（UTF-8）。
     *
     * @param file 文件路径
     * @return 文件内容
     */
    private String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("知识库 ETL 读取失败: " + file, e);
        }
    }
}
