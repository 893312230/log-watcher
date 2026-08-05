package com.smartops.domain.logwatch;

import com.smartops.common.enums.AlertLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AlertQuery} 与 {@link AlertPage} 单元测试。
 *
 * <p>验证分页参数的归一化（页码下限、每页大小上下限）与分页结果模型。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class AlertQueryTest {

    @Test
    @DisplayName("负数页码归一化为 0")
    void should_normalizeNegativePage_when_constructed() {
        AlertQuery query = new AlertQuery(null, null, null, null, null, -3, 20);

        assertThat(query.page()).isZero();
    }

    @Test
    @DisplayName("每页大小越界时收敛到 1..100")
    void should_clampSize_when_outOfRange() {
        assertThat(new AlertQuery(null, null, null, null, null, 0, 0).size()).isEqualTo(1);
        assertThat(new AlertQuery(null, null, null, null, null, 0, 500).size()).isEqualTo(100);
        assertThat(new AlertQuery(null, null, null, null, null, 0, 20).size()).isEqualTo(20);
    }

    @Test
    @DisplayName("过滤条件原样保留，允许为 null（表示不过滤）")
    void should_keepFilters_when_constructed() {
        Instant from = Instant.parse("2026-07-22T00:00:00Z");
        Instant to = Instant.parse("2026-07-22T23:59:59Z");

        AlertQuery query = new AlertQuery(AlertLevel.ERROR, "app.log", "OOM", from, to, 1, 10);

        assertThat(query.level()).isEqualTo(AlertLevel.ERROR);
        assertThat(query.source()).isEqualTo("app.log");
        assertThat(query.keyword()).isEqualTo("OOM");
        assertThat(query.from()).isEqualTo(from);
        assertThat(query.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("AlertPage 携带结果集与分页元信息")
    void should_carryItemsAndMeta_when_pageConstructed() {
        Instant now = Instant.parse("2026-07-22T10:00:00Z");
        Alert alert = Alert.create("fp", "app.log", AlertLevel.WARN, "k", "m", "", 1, now);

        AlertPage page = new AlertPage(List.of(alert), 57, 2, 10);

        assertThat(page.items()).containsExactly(alert);
        assertThat(page.total()).isEqualTo(57);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
    }
}
