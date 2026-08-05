package com.smartops.api.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PageSlice} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class PageSliceTest {

    private static final List<Integer> ALL = List.of(1, 2, 3, 4, 5);

    @Test
    @DisplayName("缺省参数返回前 100 条（小列表即全量）")
    void should_returnAll_when_defaults() {
        assertThat(PageSlice.slice(ALL, null, null)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("按页切片")
    void should_sliceByPage() {
        assertThat(PageSlice.slice(ALL, 0, 2)).containsExactly(1, 2);
        assertThat(PageSlice.slice(ALL, 1, 2)).containsExactly(3, 4);
        assertThat(PageSlice.slice(ALL, 2, 2)).containsExactly(5);
    }

    @Test
    @DisplayName("页码越界返回空列表")
    void should_returnEmpty_when_pageOutOfRange() {
        assertThat(PageSlice.slice(ALL, 9, 2)).isEmpty();
    }

    @Test
    @DisplayName("负页码按 0、size 夹紧到 [1, 500]")
    void should_clampParams() {
        assertThat(PageSlice.slice(ALL, -1, 0)).containsExactly(1);
        assertThat(PageSlice.slice(ALL, 0, 9999)).containsExactly(1, 2, 3, 4, 5);
    }
}
