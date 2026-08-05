package com.smartops.api.controller;

import com.smartops.domain.config.ServerConfig;
import com.smartops.domain.config.port.ServerConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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
 * {@link ServerConfigController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(ServerConfigController.class)
class ServerConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServerConfigRepository repository;

    @Test
    @DisplayName("list 返回空列表")
    void should_returnEmptyList() throws Exception {
        when(repository.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("create 创建服务器配置")
    void should_createServerConfig() throws Exception {
        when(repository.save(any())).thenReturn(ServerConfig.create(
                "s1", "10.0.0.1", "/opt/app.jar", "https://git/x", "/var/log/x.log",
                "desc", List.of("java"), Instant.now()).withId(1L));
        mockMvc.perform(post("/api/servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"s1\",\"host\":\"10.0.0.1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("s1"))
                .andExpect(jsonPath("$.host").value("10.0.0.1"));
    }

    @Test
    @DisplayName("get 找不到返回 404")
    void should_return404_when_notFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/servers/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("delete 成功后返回 200")
    void should_delete() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(
                ServerConfig.create("s1", null, null, null, null, null, null, Instant.now())));
        mockMvc.perform(delete("/api/servers/1"))
                .andExpect(status().isOk());
    }
}
