package com.smartops.domain.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link KnowledgeChunk} 单元测试。
 *
 * <p>验证 record 访问器、紧凑构造器校验（非空、title 归一化）
 * 以及 equals/hashCode 分支。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class KnowledgeChunkTest {

    @Nested
    @DisplayName("构造校验")
    class Construction {

        @Test
        @DisplayName("正常构造可访问全部字段")
        void should_accessAllFields_when_validConstruction() {
            KnowledgeChunk chunk = new KnowledgeChunk("id-1", "正文", "a/b.md", "标题", 0.87);

            assertThat(chunk.id()).isEqualTo("id-1");
            assertThat(chunk.content()).isEqualTo("正文");
            assertThat(chunk.source()).isEqualTo("a/b.md");
            assertThat(chunk.title()).isEqualTo("标题");
            assertThat(chunk.score()).isEqualTo(0.87);
        }

        @Test
        @DisplayName("title 为 null 时归一化为空串")
        void should_normalizeTitle_when_titleNull() {
            KnowledgeChunk chunk = new KnowledgeChunk("id-1", "正文", "a/b.md", null, 0.5);

            assertThat(chunk.title()).isEmpty();
        }

        @Test
        @DisplayName("id 为 null 时抛出 NullPointerException")
        void should_throwNPE_when_idNull() {
            assertThatThrownBy(() -> new KnowledgeChunk(null, "正文", "a/b.md", "标题", 0.5))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("content 为 null 时抛出 NullPointerException")
        void should_throwNPE_when_contentNull() {
            assertThatThrownBy(() -> new KnowledgeChunk("id-1", null, "a/b.md", "标题", 0.5))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("内容");
        }

        @Test
        @DisplayName("source 为 null 时抛出 NullPointerException")
        void should_throwNPE_when_sourceNull() {
            assertThatThrownBy(() -> new KnowledgeChunk("id-1", "正文", null, "标题", 0.5))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("来源");
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class Equality {

        @Test
        @DisplayName("全字段相等时 equals 为 true 且 hashCode 一致")
        void should_beEqual_when_allFieldsMatch() {
            KnowledgeChunk a = new KnowledgeChunk("id-1", "正文", "a/b.md", "标题", 0.87);
            KnowledgeChunk b = new KnowledgeChunk("id-1", "正文", "a/b.md", "标题", 0.87);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("任一字段不同时 equals 为 false")
        void should_notBeEqual_when_anyFieldDiffers() {
            KnowledgeChunk base = new KnowledgeChunk("id-1", "正文", "a/b.md", "标题", 0.87);

            assertThat(base).isNotEqualTo(new KnowledgeChunk("id-2", "正文", "a/b.md", "标题", 0.87));
            assertThat(base).isNotEqualTo(new KnowledgeChunk("id-1", "其他", "a/b.md", "标题", 0.87));
            assertThat(base).isNotEqualTo(new KnowledgeChunk("id-1", "正文", "c/d.md", "标题", 0.87));
            assertThat(base).isNotEqualTo(new KnowledgeChunk("id-1", "正文", "a/b.md", "他题", 0.87));
            assertThat(base).isNotEqualTo(new KnowledgeChunk("id-1", "正文", "a/b.md", "标题", 0.66));
            assertThat(base).isNotEqualTo(null);
            assertThat(base).isNotEqualTo("非 KnowledgeChunk");
        }
    }
}
