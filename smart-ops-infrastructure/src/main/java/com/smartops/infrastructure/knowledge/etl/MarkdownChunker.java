package com.smartops.infrastructure.knowledge.etl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 标题感知切分器（阶段四知识库 ETL，ADR-016）。
 *
 * <p>按 Markdown 标题行（{@code #} 至 {@code ######} 开头）将文档切为若干节，
 * 每节记录所属标题文本；超过 chunkSize 的节按行边界再切分为多个子块，
 * 保证单块不超过上限且不截断行。</p>
 *
 * <p>线程安全：无内部状态，Bean 单例可共享。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class MarkdownChunker {

    /** 单块最大字符数（近似 token 上限）。 */
    private final int chunkSize;

    /**
     * 构造切分器。
     *
     * @param chunkSize 单块最大字符数，正数
     */
    public MarkdownChunker(@Value("${smartops.knowledge.etl.chunk-size:800}") int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必须为正数，实际: " + chunkSize);
        }
        this.chunkSize = chunkSize;
    }

    /**
     * 切分一个 Markdown 文档。
     *
     * @param markdown 文档全文，非 null
     * @return 块列表（按文档顺序，index 从 0 递增）；空白文档返回空列表
     */
    public List<Chunk> chunk(String markdown) {
        List<Section> sections = splitSections(markdown);
        List<Chunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            for (String piece : splitBySize(section.text())) {
                String content = piece.trim();
                if (!content.isEmpty()) {
                    chunks.add(new Chunk(content, section.title(), chunks.size()));
                }
            }
        }
        return chunks;
    }

    /**
     * 按标题行切分文档为节。
     *
     * @param markdown 文档全文
     * @return 节列表；无标题时整体为一节（title 为空串）
     */
    private List<Section> splitSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        String currentTitle = "";
        StringBuilder current = new StringBuilder();
        for (String line : markdown.split("\n", -1)) {
            String heading = parseHeading(line);
            if (heading != null) {
                if (current.length() > 0) {
                    sections.add(new Section(currentTitle, current.toString()));
                }
                currentTitle = heading;
                current = new StringBuilder();
            }
            current.append(line).append('\n');
        }
        if (current.length() > 0) {
            sections.add(new Section(currentTitle, current.toString()));
        }
        return sections;
    }

    /**
     * 解析标题行。
     *
     * @param line 单行文本
     * @return 标题文本；非标题行返回 null
     */
    private String parseHeading(String line) {
        String trimmed = line.stripLeading();
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level > 6 || level >= trimmed.length() || trimmed.charAt(level) != ' ') {
            return null;
        }
        return trimmed.substring(level + 1).trim();
    }

    /**
     * 将超过上限的节按行边界再切分。
     *
     * @param text 节文本
     * @return 每段不超过 chunkSize 的片段列表
     */
    private List<String> splitBySize(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (current.length() > 0 && current.length() + line.length() + 1 > chunkSize) {
                pieces.add(current.toString());
                current = new StringBuilder();
            }
            current.append(line).append('\n');
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    /**
     * 切分结果块。
     *
     * @param content 块正文（已 trim，非空）
     * @param title   所属标题，无标题节为空串
     * @param index   块在文档内的序号（从 0 递增）
     */
    public record Chunk(String content, String title, int index) {
    }

    /**
     * 标题节中间态。
     *
     * @param title 节标题
     * @param text  节全文（含标题行）
     */
    private record Section(String title, String text) {
    }
}
