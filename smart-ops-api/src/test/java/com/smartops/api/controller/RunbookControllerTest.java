package com.smartops.api.controller;

import com.smartops.agent.security.ConfirmationTokenStore;
import com.smartops.domain.runbook.Runbook;
import com.smartops.domain.runbook.RunbookExecution;
import com.smartops.domain.runbook.StepResult;
import com.smartops.domain.runbook.port.RunbookExecutionRepository;
import com.smartops.domain.runbook.port.RunbookRepository;
import com.smartops.infrastructure.runbook.RunbookExecutor;
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
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RunbookController} Web 层测试。
 *
 * @author smartops
 * @since 1.0.0
 */
@WebMvcTest(RunbookController.class)
class RunbookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RunbookRepository runbookRepository;

    @MockitoBean
    private RunbookExecutionRepository executionRepository;

    @MockitoBean
    private RunbookExecutor runbookExecutor;

    @MockitoBean
    private ConfirmationTokenStore confirmationTokenStore;

    @MockitoBean(name = "runbookTaskExecutor")
    private Executor runbookTaskExecutor;

    private static final Runbook RB = new Runbook(5L, "磁盘清理", "desc", "DISK",
            List.of("检查磁盘使用", "清理临时文件"), 2, "恢复快照", true);

    private static final Runbook HIGH_RISK_RB = new Runbook(9L, "重启生产服务", "desc", "RESTART",
            List.of("重启服务"), 5, "回退", true);

    @Test
    @DisplayName("list 返回全部 Runbook")
    void should_listRunbooks() throws Exception {
        when(runbookRepository.findAll()).thenReturn(List.of(RB));
        mockMvc.perform(get("/api/runbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("磁盘清理"))
                .andExpect(jsonPath("$[0].id").value(5));
    }

    @Test
    @DisplayName("create 创建 Runbook 并返回分配 id")
    void should_createRunbook() throws Exception {
        when(runbookRepository.save(any())).thenReturn(RB);
        mockMvc.perform(post("/api/runbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"磁盘清理\",\"steps\":[\"检查磁盘使用\"],\"safetyLevel\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @DisplayName("execute 落库 RUNNING 后立即返回 executionId（异步受理）")
    void should_returnRunningImmediately_when_execute() throws Exception {
        when(runbookRepository.findById(5L)).thenReturn(Optional.of(RB));
        RunbookExecution running = new RunbookExecution(11L, 5L, Instant.now(), null, "RUNNING", List.of());
        when(executionRepository.save(any())).thenReturn(running);

        mockMvc.perform(post("/api/runbooks/5/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runbook").value("磁盘清理"))
                .andExpect(jsonPath("$.executionId").value(11))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("execute 提交的异步任务复用同一 RUNNING 记录驱动执行引擎")
    void should_driveEngineWithSameRecord_when_asyncTaskRuns() throws Exception {
        when(runbookRepository.findById(5L)).thenReturn(Optional.of(RB));
        RunbookExecution running = new RunbookExecution(11L, 5L, Instant.now(), null, "RUNNING", List.of());
        when(executionRepository.save(any())).thenReturn(running);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(runbookTaskExecutor).execute(any(Runnable.class));

        mockMvc.perform(post("/api/runbooks/5/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        verify(runbookExecutor).execute(RB, running);
    }

    @Test
    @DisplayName("execution 查询单条执行记录（轮询入口）")
    void should_returnExecution_when_found() throws Exception {
        RunbookExecution exec = new RunbookExecution(11L, 5L, Instant.now(), Instant.now(),
                "SUCCESS", List.of(new StepResult(1, "cmd", "SUCCESS", "ok")));
        when(executionRepository.findById(11L)).thenReturn(Optional.of(exec));

        mockMvc.perform(get("/api/runbooks/executions/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.stepResults[0].output").value("ok"));
    }

    @Test
    @DisplayName("execution 执行记录不存在返回 404")
    void should_return404_when_executionMissing() throws Exception {
        when(executionRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/runbooks/executions/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("execute 找不到 Runbook 返回 400")
    void should_return400_when_runbookMissing() throws Exception {
        when(runbookRepository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/runbooks/99/execute"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("execute 高危 Runbook 无令牌 → 签发待确认令牌不执行")
    void should_returnPendingConfirmation_when_highRiskWithoutToken() throws Exception {
        when(runbookRepository.findById(9L)).thenReturn(Optional.of(HIGH_RISK_RB));
        when(confirmationTokenStore.validateAndConsume(null, "runbook:9", "重启生产服务"))
                .thenReturn(false);
        when(confirmationTokenStore.issue("runbook:9", "重启生产服务")).thenReturn("token-1");

        mockMvc.perform(post("/api/runbooks/9/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConfirmation").value(true))
                .andExpect(jsonPath("$.confirmationToken").value("token-1"))
                .andExpect(jsonPath("$.safetyLevel").value(5));
    }

    @Test
    @DisplayName("execute 高危 Runbook 令牌无效 → 重新签发待确认令牌")
    void should_returnPendingConfirmation_when_tokenInvalid() throws Exception {
        when(runbookRepository.findById(9L)).thenReturn(Optional.of(HIGH_RISK_RB));
        when(confirmationTokenStore.validateAndConsume("bad-token", "runbook:9", "重启生产服务"))
                .thenReturn(false);
        when(confirmationTokenStore.issue("runbook:9", "重启生产服务")).thenReturn("token-2");

        mockMvc.perform(post("/api/runbooks/9/execute").header("X-Confirm-Token", "bad-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConfirmation").value(true))
                .andExpect(jsonPath("$.confirmationToken").value("token-2"));
    }

    @Test
    @DisplayName("execute 高危 Runbook 令牌有效 → 落库 RUNNING 异步执行")
    void should_execute_when_highRiskTokenValid() throws Exception {
        when(runbookRepository.findById(9L)).thenReturn(Optional.of(HIGH_RISK_RB));
        when(confirmationTokenStore.validateAndConsume("good-token", "runbook:9", "重启生产服务"))
                .thenReturn(true);
        RunbookExecution running = new RunbookExecution(21L, 9L, Instant.now(), null, "RUNNING", List.of());
        when(executionRepository.save(any())).thenReturn(running);

        mockMvc.perform(post("/api/runbooks/9/execute").header("X-Confirm-Token", "good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.executionId").value(21));
    }

    @Test
    @DisplayName("history 返回执行记录列表")
    void should_returnHistory() throws Exception {
        RunbookExecution exec = new RunbookExecution(11L, 5L, Instant.now(), Instant.now(),
                "SUCCESS", List.of(new StepResult(1, "cmd", "SUCCESS", "ok")));
        when(executionRepository.findByRunbookId(5L)).thenReturn(List.of(exec));

        mockMvc.perform(get("/api/runbooks/5/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].stepResults[0].output").value("ok"));
    }

    @Test
    @DisplayName("delete 删除 Runbook 返回 200")
    void should_deleteRunbook() throws Exception {
        mockMvc.perform(delete("/api/runbooks/5"))
                .andExpect(status().isOk());
    }
}
