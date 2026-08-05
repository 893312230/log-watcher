package com.smartops.api.controller;

import com.smartops.infrastructure.persistence.silence.SilenceWindowEntity;
import com.smartops.infrastructure.persistence.silence.SilenceWindowJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SilenceController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(SilenceController.class)
class SilenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SilenceWindowJpaRepository repository;

    @Test
    @DisplayName("list 返回全部静默窗口")
    void should_listSilences() throws Exception {
        SilenceWindowEntity e = new SilenceWindowEntity();
        e.setId(1L);
        e.setSourceMatcher("app.log");
        e.setStartAt(Instant.now());
        e.setEndAt(Instant.now().plusSeconds(3600));
        e.setCreatedAt(Instant.now());
        when(repository.findAll()).thenReturn(List.of(e));

        mockMvc.perform(get("/api/silences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceMatcher").value("app.log"));
    }

    @Test
    @DisplayName("create 解析时间并保存")
    void should_createSilence() throws Exception {
        when(repository.save(any())).thenAnswer(inv -> {
            SilenceWindowEntity e = inv.getArgument(0);
            e.setId(3L);
            return e;
        });
        mockMvc.perform(post("/api/silences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceMatcher\":\"app.log\",\"levelFilter\":\"ERROR\","
                                + "\"startAt\":\"2026-07-28T10:00:00Z\",\"endAt\":\"2026-07-28T12:00:00Z\","
                                + "\"reason\":\"维护窗口\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.startAt").value("2026-07-28T10:00:00Z"))
                .andExpect(jsonPath("$.reason").value("维护窗口"));
    }

    @Test
    @DisplayName("create 时间缺省时使用默认窗口")
    void should_useDefaultWindow_when_timeMissing() throws Exception {
        when(repository.save(any())).thenAnswer(inv -> {
            SilenceWindowEntity e = inv.getArgument(0);
            e.setId(4L);
            return e;
        });
        mockMvc.perform(post("/api/silences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"紧急\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.startAt").exists())
                .andExpect(jsonPath("$.endAt").exists());
    }

    @Test
    @DisplayName("delete 按 id 删除返回 200")
    void should_deleteSilence() throws Exception {
        mockMvc.perform(delete("/api/silences/3"))
                .andExpect(status().isOk());
    }
}
