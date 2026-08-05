package com.smartops.infrastructure.knowledge.etl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeEtlRunner} 单元测试。
 *
 * <p>验证：仅索引 .md 文件（含子目录、相对路径 source）、
 * source-dir 缺失时跳过、索引异常向上传播（启动快速失败）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class KnowledgeEtlRunnerTest {

    @TempDir
    Path tempDir;

    private final MarkdownChunker chunker = new MarkdownChunker(800);
    private final KnowledgeIndexer indexer = mock(KnowledgeIndexer.class);

    /**
     * 构造以 tempDir 为 source-dir 的 Runner。
     *
     * @return Runner 实例
     */
    private KnowledgeEtlRunner newRunner() {
        return new KnowledgeEtlRunner(chunker, indexer, tempDir.toString());
    }

    @Nested
    @DisplayName("扫描与索引")
    class ScanAndIndex {

        @Test
        @DisplayName("仅索引 .md 文件（含子目录），source 为相对路径")
        void should_indexOnlyMarkdown_when_mixedFiles() throws Exception {
            Files.writeString(tempDir.resolve("a.md"), "# A\n内容A\n");
            Files.writeString(tempDir.resolve("ignore.txt"), "不索引");
            Path sub = Files.createDirectory(tempDir.resolve("sub"));
            Files.writeString(sub.resolve("b.md"), "# B\n内容B\n");
            when(indexer.index(anyString(), anyList())).thenReturn(1);

            newRunner().run(new DefaultApplicationArguments());

            verify(indexer).index(eq("a.md"), anyList());
            verify(indexer).index(eq(Path.of("sub", "b.md").toString()), anyList());
            verify(indexer, never()).index(eq("ignore.txt"), anyList());
        }

        @Test
        @DisplayName("source-dir 不存在时跳过且不触碰 indexer")
        void should_skip_when_sourceDirMissing() {
            KnowledgeEtlRunner runner = new KnowledgeEtlRunner(
                    chunker, indexer, tempDir.resolve("not-exists").toString());

            runner.run(new DefaultApplicationArguments());

            verify(indexer, never()).index(anyString(), anyList());
        }

        @Test
        @DisplayName("索引异常向上传播（启动快速失败）")
        void should_propagate_when_indexerThrows() throws Exception {
            Files.writeString(tempDir.resolve("a.md"), "# A\n内容A\n");
            when(indexer.index(anyString(), anyList()))
                    .thenThrow(new IllegalStateException("embedding 维度不匹配"));

            assertThatThrownBy(() -> newRunner().run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("维度不匹配");
        }
    }

    @Nested
    @DisplayName("构造校验")
    class Construction {

        @Test
        @DisplayName("chunker 为 null 时抛出 NullPointerException")
        void should_throwNPE_when_chunkerNull() {
            assertThatThrownBy(() -> new KnowledgeEtlRunner(null, indexer, "docs/knowledge"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("indexer 为 null 时抛出 NullPointerException")
        void should_throwNPE_when_indexerNull() {
            assertThatThrownBy(() -> new KnowledgeEtlRunner(chunker, null, "docs/knowledge"))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
