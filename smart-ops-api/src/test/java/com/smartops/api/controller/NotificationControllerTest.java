package com.smartops.api.controller;

import com.smartops.infrastructure.persistence.notification.NotificationChannelEntity;
import com.smartops.infrastructure.persistence.notification.NotificationChannelJpaRepository;
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
 * {@link NotificationController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationChannelJpaRepository repository;

    private static NotificationChannelEntity channel(Long id, String name) {
        NotificationChannelEntity e = new NotificationChannelEntity();
        e.setId(id);
        e.setName(name);
        e.setType("WEBHOOK");
        e.setTargetUrl("https://example.com/hook");
        e.setEnabled(true);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    @Test
    @DisplayName("list 返回全部渠道")
    void should_listChannels() throws Exception {
        when(repository.findAll()).thenReturn(List.of(channel(1L, "ops-hook")));
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ops-hook"))
                .andExpect(jsonPath("$[0].targetUrl").value("https://example.com/hook"));
    }

    @Test
    @DisplayName("add 创建渠道")
    void should_addChannel() throws Exception {
        when(repository.save(any())).thenReturn(channel(2L, "new-hook"));
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"new-hook\",\"url\":\"https://example.com/hook\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.type").value("WEBHOOK"));
    }

    @Test
    @DisplayName("delete 按 id 删除返回 200")
    void should_deleteChannel() throws Exception {
        mockMvc.perform(delete("/api/notifications/2"))
                .andExpect(status().isOk());
    }
}
