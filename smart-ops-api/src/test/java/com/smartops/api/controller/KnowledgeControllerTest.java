package com.smartops.api.controller;

import com.smartops.domain.knowledge.KnowledgeEntry;
import com.smartops.domain.knowledge.KnowledgeEntryPage;
import com.smartops.domain.knowledge.KnowledgeEntryQuery;
import com.smartops.domain.knowledge.port.KnowledgeRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeController.class)
class KnowledgeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private KnowledgeRepository repository;

    @Test
    @DisplayName("list 返回分页结果")
    void should_returnPage() throws Exception {
        KnowledgeEntry e = KnowledgeEntry.create("test", null, "r", "s", List.of(),
                null, null, "MANUAL", null, null, "", Instant.now());
        when(repository.query(any())).thenReturn(new KnowledgeEntryPage(List.of(e), 1, 0, 20));

        mockMvc.perform(get("/api/knowledge?keyword=test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("test"));
    }

    @Test
    @DisplayName("get 返回详情")
    void should_returnDetail() throws Exception {
        KnowledgeEntry e = KnowledgeEntry.create("t", null, null, null, List.of(),
                null, null, "MANUAL", null, null, "", Instant.now());
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(e));

        mockMvc.perform(get("/api/knowledge/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("t"));
    }

    @Test
    @DisplayName("categories 返回去重分类列表")
    void should_returnCategories() throws Exception {
        when(repository.listCategories()).thenReturn(List.of("JVM", "NETWORK"));

        mockMvc.perform(get("/api/knowledge/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("JVM"))
                .andExpect(jsonPath("$[1]").value("NETWORK"));
    }
}
