package com.smartops.api.controller;

import com.smartops.infrastructure.persistence.slo.SloEntity;
import com.smartops.infrastructure.persistence.slo.SloJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SloController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(SloController.class)
class SloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SloJpaRepository repository;

    private static SloEntity slo(Long id) {
        SloEntity e = new SloEntity();
        e.setId(id);
        e.setName("api-availability");
        e.setServiceName("api");
        e.setTargetPct(99.9);
        e.setWindowDays(30);
        e.setErrorBudgetPct(0.1);
        e.setEnabled(true);
        return e;
    }

    @Test
    @DisplayName("list 返回全部 SLO")
    void should_listSlos() throws Exception {
        when(repository.findAll()).thenReturn(List.of(slo(1L)));
        mockMvc.perform(get("/api/slos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceName").value("api"))
                .andExpect(jsonPath("$[0].targetPct").value(99.9));
    }

    @Test
    @DisplayName("create 使用默认值并保存")
    void should_createSlo() throws Exception {
        when(repository.save(any())).thenAnswer(inv -> {
            SloEntity e = inv.getArgument(0);
            e.setId(2L);
            return e;
        });
        mockMvc.perform(post("/api/slos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"s\",\"serviceName\":\"api\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.targetPct").value(99.9))
                .andExpect(jsonPath("$.windowDays").value(30));
    }

    @Test
    @DisplayName("status 返回 SLO 与达成率")
    void should_returnStatus() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(slo(1L)));
        mockMvc.perform(get("/api/slos/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slo.name").value("api-availability"))
                .andExpect(jsonPath("$.currentPercent").exists());
    }

    @Test
    @DisplayName("status 找不到返回 400")
    void should_return400_when_sloMissing() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/slos/99/status"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("delete 按 id 删除返回 200")
    void should_deleteSlo() throws Exception {
        mockMvc.perform(delete("/api/slos/1"))
                .andExpect(status().isOk());
    }
}
