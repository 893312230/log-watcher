package com.smartops.infrastructure.knowledge.etl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MarkdownChunker} 单元测试。
 *
 * <p>验证标题感知切分（多级标题、无标题、伪标题行）、超长节按行边界再切分、
 * 空白文档与构造参数校验。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class MarkdownChunkerTest {

    private final MarkdownChunker chunker = new MarkdownChunker(800);

    @Nested
    @DisplayName("标题感知切分")
    class HeadingSplit {

        @Test
        @DisplayName("多级标题各成一节并记录标题文本")
        void should_splitByHeadings_when_multiLevel() {
            String markdown = "# 一级\n内容一\n## 二级\n内容二\n### 三级\n内容三\n";

            List<MarkdownChunker.Chunk> chunks = chunker.chunk(markdown);

            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0).title()).isEqualTo("一级");
            assertThat(chunks.get(0).content()).contains("内容一");
            assertThat(chunks.get(1).title()).isEqualTo("二级");
            assertThat(chunks.get(1).content()).contains("内容二");
            assertThat(chunks.get(2).title()).isEqualTo("三级");
            assertThat(chunks.get(2).index()).isEqualTo(2);
        }

        @Test
        @DisplayName("无标题文档整体为一块且标题为空串")
        void should_singleChunk_when_noHeading() {
            List<MarkdownChunker.Chunk> chunks = chunker.chunk("纯文本内容\n第二行\n");

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).title()).isEmpty();
            assertThat(chunks.get(0).index()).isEqualTo(0);
        }

        @Test
        @DisplayName("标题前的序言内容归为无标题节")
        void should_preambleChunk_when_contentBeforeFirstHeading() {
            String markdown = "序言内容\n# 标题\n正文\n";

            List<MarkdownChunker.Chunk> chunks = chunker.chunk(markdown);

            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).title()).isEmpty();
            assertThat(chunks.get(0).content()).contains("序言内容");
            assertThat(chunks.get(1).title()).isEqualTo("标题");
        }

        @Test
        @DisplayName("无空格的 # 行不视为标题")
        void should_notTreatAsHeading_when_noSpaceAfterHash() {
            List<MarkdownChunker.Chunk> chunks = chunker.chunk("#不是标题\n内容\n");

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).title()).isEmpty();
        }
    }

    @Nested
    @DisplayName("超长切分")
    class SizeSplit {

        @Test
        @DisplayName("超过 chunkSize 的节按行边界再切分且不截断行")
        void should_splitOversizedSection_when_exceedsChunkSize() {
            MarkdownChunker small = new MarkdownChunker(30);
            String markdown = "# 标题\n0123456789\n0123456789\n0123456789\n0123456789\n";

            List<MarkdownChunker.Chunk> chunks = small.chunk(markdown);

            assertThat(chunks).hasSize(2);
            assertThat(chunks).allSatisfy(chunk -> {
                assertThat(chunk.content().length()).isLessThanOrEqualTo(30);
                assertThat(chunk.title()).isEqualTo("标题");
            });
            assertThat(chunks.stream().map(MarkdownChunker.Chunk::index))
                    .containsExactly(0, 1);
            assertThat(chunks.get(0).content()).startsWith("# 标题");
            assertThat(chunks.get(1).content()).doesNotContain("# 标题");
        }

        @Test
        @DisplayName("恰好不超限时保持单块")
        void should_keepSingleChunk_when_withinLimit() {
            MarkdownChunker small = new MarkdownChunker(100);

            List<MarkdownChunker.Chunk> chunks = small.chunk("短内容\n");

            assertThat(chunks).hasSize(1);
        }
    }

    @Nested
    @DisplayName("边界与校验")
    class EdgeCases {

        @Test
        @DisplayName("空白文档返回空列表")
        void should_returnEmpty_when_blankDocument() {
            assertThat(chunker.chunk("   \n\n  ")).isEmpty();
            assertThat(chunker.chunk("")).isEmpty();
        }

        @Test
        @DisplayName("chunkSize 非正数时抛出 IllegalArgumentException")
        void should_throwIAE_when_chunkSizeNotPositive() {
            assertThatThrownBy(() -> new MarkdownChunker(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("chunkSize");
        }
    }
}
