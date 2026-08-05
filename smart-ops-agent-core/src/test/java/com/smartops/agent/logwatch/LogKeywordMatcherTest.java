package com.smartops.agent.logwatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogKeywordMatcher} 单元测试。
 *
 * <p>覆盖：内置错误关键字、自定义关键字、大小写不敏感、未命中、
 * 排除子串独立判定（isExcluded，ML 直通模式用）。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class LogKeywordMatcherTest {

    @Test
    @DisplayName("内置错误关键字 ERROR 命中")
    void should_matchBuiltinKeyword_when_contentContainsError() {
        LogKeywordMatcher matcher = new LogKeywordMatcher(List.of());

        Optional<String> hit = matcher.match("2026-07-22 10:00:00 ERROR connection refused");

        assertThat(hit).hasValue("ERROR");
    }

    @Test
    @DisplayName("内置关键字 Exception 命中")
    void should_matchBuiltinKeyword_when_contentContainsException() {
        LogKeywordMatcher matcher = new LogKeywordMatcher(List.of());

        assertThat(matcher.match("java.lang.NullPointerException")).hasValue("Exception");
    }

    @Test
    @DisplayName("自定义关键字命中并返回该关键字")
    void should_matchCustomKeyword_when_configured() {
        LogKeywordMatcher matcher = new LogKeywordMatcher(List.of("余额不足", "库存锁定失败"));

        assertThat(matcher.match("2026-07-22 10:00:00 INFO 扣减失败：余额不足"))
                .hasValue("余额不足");
    }

    @Test
    @DisplayName("关键字匹配大小写不敏感")
    void should_beCaseInsensitive_when_matching() {
        LogKeywordMatcher matcher = new LogKeywordMatcher(List.of("DeadLock"));

        assertThat(matcher.match("found DEADLOCK in thread pool")).hasValue("DeadLock");
    }

    @Test
    @DisplayName("普通 INFO 日志未命中任何关键字")
    void should_returnEmpty_when_nothingMatches() {
        LogKeywordMatcher matcher = new LogKeywordMatcher(List.of("余额不足"));

        assertThat(matcher.match("2026-07-22 10:00:00 INFO service started")).isEmpty();
    }

    @Test
    @DisplayName("命中排除子串时即使含错误关键字也不告警（防自引用告警风暴）")
    void should_returnEmpty_when_excludeKeywordMatches() {
        LogKeywordMatcher matcher = new LogKeywordMatcher(List.of(),
                List.of("c.s.agent.orchestrator", "c.s.agent.logwatch"));

        assertThat(matcher.match(
                "2026-07-22 10:00:00 INFO c.s.agent.orchestrator.SupervisorAgent : 告警级别：ERROR"))
                .isEmpty();
        assertThat(matcher.match(
                "2026-07-22 10:00:00 INFO c.s.api.controller.AgentController : 真实业务 ERROR"))
                .hasValue("ERROR");
    }

    @Test
    @DisplayName("排除子串匹配大小写不敏感")
    void should_beCaseInsensitive_when_excluding() {
        LogKeywordMatcher matcher = new LogKeywordMatcher(List.of(), List.of("LogWatch"));

        assertThat(matcher.match("found ERROR in logwatch pipeline")).isEmpty();
    }

    @Test
    @DisplayName("自定义关键字列表为 null 时按空列表处理")
    void should_tolerateNullCustomKeywords_when_constructed() {
        LogKeywordMatcher matcher = new LogKeywordMatcher(null);

        assertThat(matcher.match("ERROR boom")).hasValue("ERROR");
    }

    @Test
    @DisplayName("isExcluded：命中排除子串返回 true（大小写不敏感），未命中返回 false")
    void should_reportExcluded_when_excludeSubstringHits() {
        LogKeywordMatcher matcher = new LogKeywordMatcher(List.of(), List.of("LogWatch"));

        assertThat(matcher.isExcluded("c.s.agent.LOGWATCH.MlClassifyLayer : 救援")).isTrue();
        assertThat(matcher.isExcluded("Connection refused by db-01")).isFalse();
    }

    @Test
    @DisplayName("isExcluded：未配置排除子串时恒 false")
    void should_neverExcluded_when_noExcludesConfigured() {
        LogKeywordMatcher matcher = new LogKeywordMatcher(List.of("余额不足"));

        assertThat(matcher.isExcluded("任意日志内容")).isFalse();
    }
}
