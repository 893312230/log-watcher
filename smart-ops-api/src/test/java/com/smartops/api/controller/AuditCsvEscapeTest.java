package com.smartops.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditController#csv(String)} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class AuditCsvEscapeTest {

    @Test
    @DisplayName("null 转空引号对")
    void should_escapeNull() {
        assertThat(AuditController.csv(null)).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("含逗号与引号的值被引号包裹且引号双写")
    void should_escapeCommasAndQuotes() {
        assertThat(AuditController.csv("a,b\"c")).isEqualTo("\"a,b\"\"c\"");
    }

    @Test
    @DisplayName("前导公式字符加单引号前缀防注入")
    void should_prefixFormulaChars() {
        assertThat(AuditController.csv("=cmd|'/c calc'!A1")).isEqualTo("\"'=cmd|'/c calc'!A1\"");
        assertThat(AuditController.csv("+1+1")).isEqualTo("\"'+1+1\"");
        assertThat(AuditController.csv("-2-3")).isEqualTo("\"'-2-3\"");
        assertThat(AuditController.csv("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
    }

    @Test
    @DisplayName("普通文本仅加引号")
    void should_quotePlainText() {
        assertThat(AuditController.csv("LLM_CALL")).isEqualTo("\"LLM_CALL\"");
    }
}
