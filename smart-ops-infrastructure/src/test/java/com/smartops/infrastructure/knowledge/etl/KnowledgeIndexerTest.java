package com.smartops.infrastructure.knowledge.etl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeIndexer} 单元测试。
 *
 * <p>验证文档构建（幂等 id、metadata 映射）、维度探针校验（通过/不匹配/仅一次）、
 * 空块短路。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class KnowledgeIndexerTest {

    private VectorStore vectorStore;
    private EmbeddingModel embeddingModel;
    private KnowledgeIndexer indexer;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(new float[1024]);
        indexer = new KnowledgeIndexer(vectorStore, embeddingModel, 1024);
    }

    @Nested
    @DisplayName("文档构建")
    class DocumentBuilding {

        @Test
        @DisplayName("块转换为 Document：幂等 id + source/title metadata")
        @SuppressWarnings("unchecked")
        void should_buildDocuments_when_indexing() {
            List<MarkdownChunker.Chunk> chunks = List.of(
                    new MarkdownChunker.Chunk("内容零", "标题", 0),
                    new MarkdownChunker.Chunk("内容一", "标题", 1));

            int written = indexer.index("runbooks/a.md", chunks);

            assertThat(written).isEqualTo(2);
            ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
            verify(vectorStore).add(captor.capture());
            List<Document> docs = captor.getValue();
            assertThat(docs).hasSize(2);
            assertThat(docs.get(0).getId()).isEqualTo(KnowledgeIndexer.stableId("runbooks/a.md", 0));
            assertThat(docs.get(0).getText()).isEqualTo("内容零");
            assertThat(docs.get(0).getMetadata())
                    .containsEntry("source", "runbooks/a.md")
                    .containsEntry("title", "标题");
            assertThat(docs.get(1).getId()).isEqualTo(KnowledgeIndexer.stableId("runbooks/a.md", 1));
        }

        @Test
        @DisplayName("空块列表直接返回 0 且不触碰 VectorStore/EmbeddingModel")
        void should_shortCircuit_when_chunksEmpty() {
            int written = indexer.index("runbooks/a.md", List.of());

            assertThat(written).isZero();
            verify(vectorStore, never()).add(anyList());
            verify(embeddingModel, never()).embed(anyString());
        }
    }

    @Nested
    @DisplayName("维度校验")
    class DimensionValidation {

        @Test
        @DisplayName("维度不匹配时抛出 IllegalStateException 且不写入")
        void should_failFast_when_dimensionsMismatch() {
            when(embeddingModel.embed(anyString())).thenReturn(new float[512]);

            assertThatThrownBy(() -> indexer.index("a.md",
                    List.of(new MarkdownChunker.Chunk("内容", "标题", 0))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("1024")
                    .hasMessageContaining("512");
            verify(vectorStore, never()).add(anyList());
        }

        @Test
        @DisplayName("探针校验仅执行一次")
        void should_validateOnlyOnce_when_multipleIndexCalls() {
            indexer.index("a.md", List.of(new MarkdownChunker.Chunk("内容", "标题", 0)));
            indexer.index("b.md", List.of(new MarkdownChunker.Chunk("内容", "标题", 0)));

            verify(embeddingModel, times(1)).embed(anyString());
        }
    }

    @Nested
    @DisplayName("stableId")
    class StableId {

        @Test
        @DisplayName("同一 source+index 生成相同 id，不同输入生成不同 id")
        void should_beDeterministic_when_sameInput() {
            String first = KnowledgeIndexer.stableId("a.md", 0);

            assertThat(KnowledgeIndexer.stableId("a.md", 0)).isEqualTo(first);
            assertThat(KnowledgeIndexer.stableId("a.md", 1)).isNotEqualTo(first);
            assertThat(KnowledgeIndexer.stableId("b.md", 0)).isNotEqualTo(first);
        }
    }

    @Nested
    @DisplayName("构造校验")
    class Construction {

        @Test
        @DisplayName("vectorStore 为 null 时抛出 NullPointerException")
        void should_throwNPE_when_vectorStoreNull() {
            assertThatThrownBy(() -> new KnowledgeIndexer(null, embeddingModel, 1024))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("embeddingModel 为 null 时抛出 NullPointerException")
        void should_throwNPE_when_embeddingModelNull() {
            assertThatThrownBy(() -> new KnowledgeIndexer(vectorStore, null, 1024))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
