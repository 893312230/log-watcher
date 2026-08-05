package com.smartops.api.controller;

import com.smartops.infrastructure.persistence.integration.IntegrationEntity;
import com.smartops.infrastructure.persistence.integration.IntegrationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link IntegrationController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(IntegrationController.class)
class IntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IntegrationJpaRepository repository;

    @Test
    @DisplayName("list 返回全部集成")
    void should_listIntegrations() throws Exception {
        IntegrationEntity e = new IntegrationEntity();
        e.setId(1L);
        e.setType("JIRA");
        e.setName("jira-prod");
        e.setEnabled(true);
        when(repository.findAll()).thenReturn(List.of(e));

        mockMvc.perform(get("/api/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("JIRA"));
    }

    @Test
    @DisplayName("add 序列化 config 为 JSON 文本")
    void should_addIntegration() throws Exception {
        when(repository.save(any())).thenAnswer(inv -> {
            IntegrationEntity e = inv.getArgument(0);
            e.setId(2L);
            return e;
        });
        mockMvc.perform(post("/api/integrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GITHUB\",\"name\":\"repo\",\"config\":{\"repo\":\"a/b\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.configJson").value("{\"repo\":\"a/b\"}"));
    }

    @Test
    @DisplayName("add config 缺省时 configJson 为 null")
    void should_addIntegration_when_configMissing() throws Exception {
        when(repository.save(any())).thenAnswer(inv -> {
            IntegrationEntity e = inv.getArgument(0);
            e.setId(3L);
            return e;
        });
        mockMvc.perform(post("/api/integrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bare\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.type").value("WEBHOOK"))
                .andExpect(jsonPath("$.configJson").doesNotExist());
    }

    @Test
    @DisplayName("delete 按 id 删除返回 200")
    void should_deleteIntegration() throws Exception {
        mockMvc.perform(delete("/api/integrations/2"))
                .andExpect(status().isOk());
    }
}
