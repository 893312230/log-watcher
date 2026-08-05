package com.smartops.api.controller;

import com.smartops.common.enums.AlertLevel;
import com.smartops.common.enums.AlertStatus;
import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.AlertPage;
import com.smartops.domain.logwatch.AlertQuery;
import com.smartops.domain.logwatch.port.AlertRepository;
import com.smartops.domain.knowledge.port.KnowledgeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AlertController} Web 层测试（@WebMvcTest 切片）。
 *
 * <p>AlertRepository 以 Mockito 替身注入，验证查询参数绑定、
 * 分页结果映射、详情 404 与 ack 状态流转，不启动完整容器。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(AlertController.class)
class AlertControllerTest {

    private static final Instant T1 = Instant.parse("2026-07-22T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertRepository repository;

    @MockitoBean
    private KnowledgeRepository knowledgeRepository;

    @MockitoBean
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private Alert alert(long id) {
        return Alert.create("fp-" + id, "app.log", AlertLevel.ERROR, "ERROR", "摘要",
                "堆栈", 3, T1)
                .withId(id);
    }

    @Test
    @DisplayName("分页查询透传过滤条件并返回统一分页视图")
    void should_returnPage_when_listWithFilters() throws Exception {
        when(repository.query(any())).thenReturn(new AlertPage(List.of(alert(1L)), 1, 0, 20));

        mockMvc.perform(get("/api/alerts")
                        .param("level", "ERROR")
                        .param("source", "app.log")
                        .param("from", "2026-07-22T09:00:00Z")
                        .param("to", "2026-07-22T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].level").value("ERROR"));

        ArgumentCaptor<AlertQuery> captor = ArgumentCaptor.forClass(AlertQuery.class);
        verify(repository).query(captor.capture());
        AlertQuery query = captor.getValue();
        assertThat(query.level()).isEqualTo(AlertLevel.ERROR);
        assertThat(query.source()).isEqualTo("app.log");
        assertThat(query.from()).isEqualTo(Instant.parse("2026-07-22T09:00:00Z"));
        assertThat(query.to()).isEqualTo(Instant.parse("2026-07-22T11:00:00Z"));
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("不传参数时使用默认分页")
    void should_useDefaultPaging_when_noParams() throws Exception {
        when(repository.query(any())).thenReturn(new AlertPage(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        ArgumentCaptor<AlertQuery> captor = ArgumentCaptor.forClass(AlertQuery.class);
        verify(repository).query(captor.capture());
        assertThat(captor.getValue().page()).isZero();
        assertThat(captor.getValue().size()).isEqualTo(20);
        assertThat(captor.getValue().level()).isNull();
    }

    @Test
    @DisplayName("详情查询命中返回告警视图")
    void should_returnAlert_when_detailFound() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(alert(1L)));

        mockMvc.perform(get("/api/alerts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.source").value("app.log"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("详情查询未命中返回 404")
    void should_return404_when_detailMissing() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/alerts/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ack 将状态更新为 ACKED 并返回更新后视图")
    void should_ackAlert_when_exists() throws Exception {
        when(repository.updateStatus(1L, AlertStatus.ACKED))
                .thenReturn(Optional.of(alert(1L).withStatus(AlertStatus.ACKED, T1)));

        mockMvc.perform(post("/api/alerts/1/ack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKED"));

        verify(repository).updateStatus(1L, AlertStatus.ACKED);
    }

    @Test
    @DisplayName("ack 未命中返回 404")
    void should_return404_when_ackMissing() throws Exception {
        when(repository.updateStatus(99L, AlertStatus.ACKED)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/alerts/99/ack"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("dailyStats 默认 7 天且缺失日期补零")
    void should_returnDailyStats_when_defaultDays() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        when(repository.countByDay(any()))
                .thenReturn(java.util.Map.of(today, 3L));

        mockMvc.perform(get("/api/alerts/stats/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[6].date").value(today.toString()))
                .andExpect(jsonPath("$[6].count").value(3))
                .andExpect(jsonPath("$[0].count").value(0));
    }

    @Test
    @DisplayName("dailyStats days 参数越界时被夹紧")
    void should_clampDays_when_outOfRange() throws Exception {
        when(repository.countByDay(any())).thenReturn(java.util.Map.of());

        mockMvc.perform(get("/api/alerts/stats/daily").param("days", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(90));

        mockMvc.perform(get("/api/alerts/stats/daily").param("days", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
