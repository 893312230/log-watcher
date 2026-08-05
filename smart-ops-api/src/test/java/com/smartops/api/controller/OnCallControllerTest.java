package com.smartops.api.controller;

import com.smartops.infrastructure.persistence.oncall.OnCallRotationEntity;
import com.smartops.infrastructure.persistence.oncall.OnCallRotationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OnCallController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(OnCallController.class)
class OnCallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnCallRotationJpaRepository repository;

    @Test
    @DisplayName("current 无数据时播种默认排班并返回")
    void should_seedDefault_when_empty() throws Exception {
        when(repository.findByName("default")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(get("/api/oncall/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day").exists())
                .andExpect(jsonPath("$.person").exists())
                .andExpect(jsonPath("$.rotation.MON").value("值班员A"));
    }

    @Test
    @DisplayName("current 读取已有排班")
    void should_readExistingRotation() throws Exception {
        OnCallRotationEntity e = new OnCallRotationEntity();
        e.setId(1L);
        e.setName("default");
        e.setMembersJson("{\"MON\":\"张三\",\"TUE\":\"李四\"}");
        when(repository.findByName("default")).thenReturn(Optional.of(e));

        mockMvc.perform(get("/api/oncall/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rotation.MON").value("张三"));
    }

    @Test
    @DisplayName("update 合并新排班并保存")
    void should_mergeRotation_when_update() throws Exception {
        OnCallRotationEntity e = new OnCallRotationEntity();
        e.setId(1L);
        e.setName("default");
        e.setMembersJson("{\"MON\":\"张三\"}");
        when(repository.findByName("default")).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/oncall/rotation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"TUE\":\"王五\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.MON").value("张三"))
                .andExpect(jsonPath("$.TUE").value("王五"));
    }
}
