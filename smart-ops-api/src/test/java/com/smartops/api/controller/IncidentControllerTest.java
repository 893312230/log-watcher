package com.smartops.api.controller;

import com.smartops.common.enums.AlertLevel;
import com.smartops.domain.knowledge.port.KnowledgeRepository;
import com.smartops.domain.logwatch.Alert;
import com.smartops.domain.logwatch.AlertPage;
import com.smartops.domain.logwatch.port.AlertRepository;
import com.smartops.infrastructure.chat.ChatService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link IncidentController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertRepository alertRepo;

    @MockitoBean
    private KnowledgeRepository knowledgeRepo;

    @MockitoBean
    private ChatService chatService;

    private static Alert alert(long id, String source, Instant createdAt) {
        return Alert.create("fp-" + id, source, AlertLevel.ERROR, "",
                "msg-" + id, "stack", 2, createdAt).withId(id);
    }

    @Test
    @DisplayName("事件列表按来源+5分钟窗口分组，id 取组内首条告警 id（跨请求稳定）")
    void should_groupWithStableId() throws Exception {
        Instant base = Instant.ofEpochSecond(1_700_000_100L);
        Alert newest = alert(2, "svc-a", base.plusSeconds(60));
        Alert older = alert(1, "svc-a", base);
        Alert otherSource = alert(3, "svc-b", base);
        when(alertRepo.query(any())).thenReturn(new AlertPage(List.of(newest, older, otherSource), 3, 0, 100));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].alertCount").value(2))
                .andExpect(jsonPath("$[0].source").value("svc-a"))
                .andExpect(jsonPath("$[1].id").value(3))
                .andExpect(jsonPath("$[1].alertCount").value(1));
    }

    @Test
    @DisplayName("postmortem 按 path id 定位事件组并落库复盘报告")
    void should_generatePostmortem_when_incidentFound() throws Exception {
        Instant base = Instant.ofEpochSecond(1_700_000_100L);
        when(alertRepo.query(any())).thenReturn(new AlertPage(
                List.of(alert(2, "svc-a", base), alert(1, "svc-a", base)), 2, 0, 100));
        when(chatService.chatWithSystemPrompt(anyString(), anyString())).thenReturn("复盘报告正文");
        when(knowledgeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/incidents/2/postmortem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("事后复盘 #2"));

        verify(chatService).chatWithSystemPrompt(anyString(),
                org.mockito.ArgumentMatchers.contains("msg-2"));
        verify(knowledgeRepo).save(any());
    }

    @Test
    @DisplayName("postmortem 事件 id 不存在 → 404")
    void should_return404_when_incidentMissing() throws Exception {
        when(alertRepo.query(any())).thenReturn(new AlertPage(List.of(), 0, 0, 100));

        mockMvc.perform(post("/api/incidents/99/postmortem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
