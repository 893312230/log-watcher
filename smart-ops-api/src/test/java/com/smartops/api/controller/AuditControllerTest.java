package com.smartops.api.controller;

import com.smartops.common.enums.AuditEventType;
import com.smartops.domain.audit.AuditEvent;
import com.smartops.domain.audit.AuditEventPage;
import com.smartops.domain.audit.AuditEventQuery;
import com.smartops.domain.audit.port.AuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuditController} Web 层测试（@WebMvcTest 切片）。
 *
 * <p>AuditRepository 以 Mockito 替身注入，验证查询参数绑定与
 * 分页结果映射，不启动完整容器。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(AuditController.class)
class AuditControllerTest {

    private static final Instant T1 = Instant.parse("2026-07-22T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditRepository repository;

    private AuditEvent event(long id) {
        return AuditEvent.create(AuditEventType.LLM_CALL, "conv-" + id, "chatService",
                null, "摘要", true, 120, T1).withId(id);
    }

    @Test
    @DisplayName("分页查询透传过滤条件并返回统一分页视图")
    void should_returnPage_when_listWithFilters() throws Exception {
        when(repository.query(any())).thenReturn(new AuditEventPage(List.of(event(1L)), 1, 0, 20));

        mockMvc.perform(get("/api/audit/events")
                        .param("eventType", "LLM_CALL")
                        .param("traceId", "conv-1")
                        .param("from", "2026-07-22T09:00:00Z")
                        .param("to", "2026-07-22T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].eventType").value("LLM_CALL"))
                .andExpect(jsonPath("$.items[0].latencyMs").value(120));

        ArgumentCaptor<AuditEventQuery> captor = ArgumentCaptor.forClass(AuditEventQuery.class);
        verify(repository).query(captor.capture());
        AuditEventQuery query = captor.getValue();
        assertThat(query.eventType()).isEqualTo(AuditEventType.LLM_CALL);
        assertThat(query.traceId()).isEqualTo("conv-1");
        assertThat(query.from()).isEqualTo(Instant.parse("2026-07-22T09:00:00Z"));
        assertThat(query.to()).isEqualTo(Instant.parse("2026-07-22T11:00:00Z"));
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("不传参数时使用默认分页")
    void should_useDefaultPaging_when_noParams() throws Exception {
        when(repository.query(any())).thenReturn(new AuditEventPage(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/audit/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        ArgumentCaptor<AuditEventQuery> captor = ArgumentCaptor.forClass(AuditEventQuery.class);
        verify(repository).query(captor.capture());
        assertThat(captor.getValue().page()).isZero();
        assertThat(captor.getValue().size()).isEqualTo(20);
        assertThat(captor.getValue().eventType()).isNull();
    }

    @Test
    @DisplayName("显式分页参数透传")
    void should_passPagingParams_when_given() throws Exception {
        when(repository.query(any())).thenReturn(new AuditEventPage(List.of(), 0, 2, 5));

        mockMvc.perform(get("/api/audit/events")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<AuditEventQuery> captor = ArgumentCaptor.forClass(AuditEventQuery.class);
        verify(repository).query(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(2);
        assertThat(captor.getValue().size()).isEqualTo(5);
    }

    @Test
    @DisplayName("detail 命中返回单条事件")
    void should_returnEvent_when_detailFound() throws Exception {
        when(repository.findById(7L)).thenReturn(java.util.Optional.of(event(7L)));

        mockMvc.perform(get("/api/audit/events/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.traceId").value("conv-7"))
                .andExpect(jsonPath("$.eventType").value("LLM_CALL"));
    }

    @Test
    @DisplayName("detail 未命中返回 404")
    void should_return404_when_detailMissing() throws Exception {
        when(repository.findById(99L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/audit/events/99"))
                .andExpect(status().isNotFound());
    }
}
