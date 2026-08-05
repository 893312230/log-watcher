package com.smartops.agent.logwatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StackTraceParser} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class StackTraceParserTest {

    private final StackTraceParser parser = new StackTraceParser();

    @Test
    @DisplayName("解析标准堆栈帧（含行号与无行号）")
    void should_parseStandardFrames() {
        String trace = """
                java.lang.IllegalStateException: boom
                \tat com.example.OrderService.pay(OrderService.java:42)
                \tat com.example.Main.main(Main.java:10)
                """;

        List<StackTraceParser.CodeLocation> locations = parser.parse(trace);

        assertThat(locations).hasSize(2);
        assertThat(locations.get(0).className()).isEqualTo("com.example.OrderService.pay");
        assertThat(locations.get(0).fileName()).isEqualTo("OrderService.java");
        assertThat(locations.get(0).lineNumber()).isEqualTo(42);
        assertThat(locations.get(1).lineNumber()).isEqualTo(10);
    }

    @Test
    @DisplayName("null 或空白堆栈 → 空列表")
    void should_returnEmpty_when_blank() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }

    @Test
    @DisplayName("Native Method 帧 → fileName 为 Native Method，行号 0（帧模式与原生模式各命中一次）")
    void should_parseNativeFrames() {
        List<StackTraceParser.CodeLocation> locations =
                parser.parse("\tat sun.misc.Unsafe.park(Native Method)");

        assertThat(locations).hasSize(2);
        assertThat(locations).allMatch(loc ->
                "Native Method".equals(loc.fileName()) && loc.lineNumber() == 0);
    }

    @Test
    @DisplayName("超过 10 帧时截断到 10")
    void should_capAtTenFrames() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            sb.append("\tat com.example.C.m").append(i).append("(C.java:").append(i + 1).append(")\n");
        }

        assertThat(parser.parse(sb.toString())).hasSize(10);
    }

    @Test
    @DisplayName("formatForPrompt：空定位或空仓库地址 → 空串")
    void should_returnEmptyPrompt_when_nothingToFormat() {
        StackTraceParser.CodeLocation loc =
                new StackTraceParser.CodeLocation("com.example.C.m", "C.java", 7);
        assertThat(parser.formatForPrompt(List.of(), "https://git/x")).isEmpty();
        assertThat(parser.formatForPrompt(List.of(loc), null)).isEmpty();
        assertThat(parser.formatForPrompt(List.of(loc), "  ")).isEmpty();
    }

    @Test
    @DisplayName("formatForPrompt：正常输出且行号 0 时不拼行号，最多 5 条")
    void should_formatPromptWithCap() {
        List<StackTraceParser.CodeLocation> locations = List.of(
                new StackTraceParser.CodeLocation("com.example.A.a", "A.java", 1),
                new StackTraceParser.CodeLocation("com.example.B.b", "B.java", 0),
                new StackTraceParser.CodeLocation("com.example.C.c", "C.java", 3),
                new StackTraceParser.CodeLocation("com.example.D.d", "D.java", 4),
                new StackTraceParser.CodeLocation("com.example.E.e", "E.java", 5),
                new StackTraceParser.CodeLocation("com.example.F.f", "F.java", 6));

        String prompt = parser.formatForPrompt(locations, "https://git/x");

        assertThat(prompt).contains("【代码定位】")
                .contains("1. com.example.A.a (A.java:1)")
                .contains("2. com.example.B.b (B.java)")
                .contains("5. com.example.E.e (E.java:5)")
                .doesNotContain("F.f");
    }
}
