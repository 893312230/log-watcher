package com.smartops.infrastructure.runbook;

import com.smartops.domain.runbook.Runbook;
import com.smartops.domain.runbook.RunbookExecution;
import com.smartops.domain.runbook.port.RunbookExecutionRepository;
import com.smartops.infrastructure.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RunbookExecutor} 单元测试（HttpClient 与 LLM 全部桩化）。
 *
 * @author smartops
 * @since 1.0.0
 */
class RunbookExecutorTest {

    private RunbookExecutionRepository repository;
    private ChatService chatService;
    private HttpClient httpClient;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private RunbookExecutor executor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(RunbookExecutionRepository.class);
        chatService = mock(ChatService.class);
        httpClient = mock(HttpClient.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        executor = new RunbookExecutor(repository, chatService, httpClient,
                eventPublisher, false, List.of(), 1);
        when(repository.save(any())).thenAnswer(inv -> {
            RunbookExecution e = inv.getArgument(0);
            return e.id() == null
                    ? new RunbookExecution(100L, e.runbookId(), e.startedAt(),
                            e.finishedAt(), e.status(), e.stepResults())
                    : e;
        });
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> responseWith(int status) {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn("body");
        return resp;
    }

    private org.mockito.stubbing.OngoingStubbing<HttpResponse<String>> whenSend() throws Exception {
        return org.mockito.Mockito.when(httpClient.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()));
    }

    @Test
    @DisplayName("LLM 步骤全部成功 → SUCCESS 且每步结果落库")
    void should_succeed_when_allLlmStepsPass() {
        Runbook rb = new Runbook(5L, "巡检", "d", "K",
                List.of("检查磁盘", "检查内存"), 1, null, true);
        when(chatService.chatWithSystemPrompt(any(), any())).thenReturn("巡检正常");

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.stepResults()).hasSize(2);
        assertThat(result.stepResults().get(0).status()).isEqualTo("SUCCESS");
        assertThat(result.stepResults().get(0).output()).isEqualTo("巡检正常");
        verify(repository, times(2)).save(any());
    }

    @Test
    @DisplayName("LLM 步骤异常 → 失败即停，后续步骤 SKIPPED，终态 FAILED")
    void should_stopAndSkip_when_llmStepThrows() {
        Runbook rb = new Runbook(5L, "巡检", "d", "K",
                List.of("步骤一", "步骤二", "步骤三"), 1, null, true);
        when(chatService.chatWithSystemPrompt(any(), any()))
                .thenThrow(new RuntimeException("LLM 不可用"));

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults()).hasSize(3);
        assertThat(result.stepResults().get(0).status()).isEqualTo("FAILED");
        assertThat(result.stepResults().get(0).output()).contains("LLM 不可用");
        assertThat(result.stepResults().get(1).status()).isEqualTo("SKIPPED");
        assertThat(result.stepResults().get(2).status()).isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("HTTP 步骤 2xx → SUCCESS")
    void should_succeed_when_http2xx() throws Exception {
        Runbook rb = new Runbook(5L, "健康检查", "d", "K",
                List.of("HTTP GET https://example.com/health"), 1, null, true);
        HttpResponse<String> resp = responseWith(200);
        whenSend().thenReturn(resp);

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.stepResults().get(0).output()).contains("200");
    }

    @Test
    @DisplayName("HTTP 步骤非 2xx → 按配置重试后 FAILED")
    void should_retryAndFail_when_httpNon2xx() throws Exception {
        Runbook rb = new Runbook(5L, "健康检查", "d", "K",
                List.of("HTTP GET https://example.com/health"), 1, null, true);
        HttpResponse<String> resp = responseWith(500);
        whenSend().thenReturn(resp);

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("HTTP 步骤首次失败重试成功 → SUCCESS")
    void should_succeedAfterRetry_when_httpRecovers() throws Exception {
        Runbook rb = new Runbook(5L, "健康检查", "d", "K",
                List.of("HTTP GET https://example.com/health"), 1, null, true);
        HttpResponse<String> first = responseWith(500);
        HttpResponse<String> second = responseWith(200);
        whenSend().thenReturn(first).thenReturn(second);

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("HTTP 步骤 POST 方法与非默认缺省解析")
    void should_parsePostMethod_when_httpStepHasMethod() throws Exception {
        Runbook rb = new Runbook(5L, "触发", "d", "K",
                List.of("HTTP POST https://example.com/hook"), 1, null, true);
        HttpResponse<String> resp = responseWith(201);
        whenSend().thenReturn(resp);

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.stepResults().get(0).output()).startsWith("POST ");
    }

    @Test
    @DisplayName("HTTP 步骤缺省方法与缺省 URL 走默认值（默认 URL 被 SSRF 拦截）")
    void should_useDefaults_when_httpStepIncomplete() {
        Runbook rb = new Runbook(5L, "默认", "d", "K",
                List.of("HTTP "), 1, null, true);

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults().get(0).output()).contains("内网");
    }

    @Test
    @DisplayName("HTTP 步骤 SSRF 地址被拦截 → FAILED 不重试")
    void should_fail_when_httpUrlIsInternal() {
        Runbook rb = new Runbook(5L, "探测", "d", "K",
                List.of("HTTP GET http://169.254.169.254/latest"), 1, null, true);

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults().get(0).output()).contains("内网");
    }

    @Test
    @DisplayName("WEBHOOK 步骤 4xx 以下 → SUCCESS，4xx → FAILED")
    void should_evaluateWebhookStatus() throws Exception {
        Runbook ok = new Runbook(5L, "通知", "d", "K",
                List.of("WEBHOOK POST https://example.com/hook"), 1, null, true);
        HttpResponse<String> okResp = responseWith(204);
        whenSend().thenReturn(okResp);
        assertThat(executor.execute(ok).status()).isEqualTo("SUCCESS");

        Runbook bad = new Runbook(6L, "通知", "d", "K",
                List.of("WEBHOOK POST https://example.com/hook"), 1, null, true);
        HttpResponse<String> badResp = responseWith(404);
        whenSend().thenReturn(badResp);
        assertThat(executor.execute(bad).status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("SCRIPT 步骤默认禁用 → FAILED")
    void should_failScript_when_disabled() {
        Runbook rb = new Runbook(5L, "脚本", "d", "K",
                List.of("SCRIPT echo hello"), 1, null, true);

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults().get(0).output()).contains("未启用");
    }

    @Test
    @DisplayName("SCRIPT 步骤启用但命令不在白名单 → FAILED")
    void should_failScript_when_notInAllowlist() {
        RunbookExecutor scriptExecutor = new RunbookExecutor(repository, chatService, httpClient,
                null, true, List.of("uptime"), 1);
        Runbook rb = new Runbook(5L, "脚本", "d", "K",
                List.of("SCRIPT rm -rf /tmp/x"), 1, null, true);

        RunbookExecution result = scriptExecutor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults().get(0).output()).contains("白名单");
    }

    @Test
    @DisplayName("执行失败且配置回滚步骤 → 回滚步骤依次执行并加 ROLLBACK 前缀")
    void should_executeRollback_when_failed() {
        Runbook rb = new Runbook(5L, "发布", "d", "K",
                List.of("发布新版本"), 4, "回退到旧版本\n通知值班", true);
        when(chatService.chatWithSystemPrompt(any(), any()))
                .thenThrow(new RuntimeException("发布失败"))
                .thenReturn("回退完成")
                .thenReturn("通知完成");

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults()).hasSize(3);
        assertThat(result.stepResults().get(1).command()).startsWith("ROLLBACK: ");
        assertThat(result.stepResults().get(1).status()).isEqualTo("SUCCESS");
        assertThat(result.stepResults().get(2).seq()).isEqualTo(3);
    }

    @Test
    @DisplayName("回滚步骤为空或空白时不追加回滚结果")
    void should_skipRollback_when_blank() {
        Runbook rb = new Runbook(5L, "发布", "d", "K",
                List.of("发布新版本"), 4, "  ", true);
        when(chatService.chatWithSystemPrompt(any(), any()))
                .thenThrow(new RuntimeException("发布失败"));

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults()).hasSize(1);
    }

    @Test
    @DisplayName("超长输出被截断到 4000 字符")
    void should_truncateLongOutput() {
        Runbook rb = new Runbook(5L, "巡检", "d", "K",
                List.of("输出大日志"), 1, null, true);
        when(chatService.chatWithSystemPrompt(any(), any())).thenReturn("x".repeat(5000));

        RunbookExecution result = executor.execute(rb);

        assertThat(result.stepResults().get(0).output()).hasSize(4000);
    }

    @Test
    @DisplayName("先落 RUNNING 再落终态（边执行边落库顺序）")
    void should_saveRunningFirst_thenFinal() {
        Runbook rb = new Runbook(5L, "巡检", "d", "K",
                List.of("检查磁盘"), 1, null, true);
        when(chatService.chatWithSystemPrompt(any(), any())).thenReturn("ok");

        executor.execute(rb);

        ArgumentCaptor<RunbookExecution> captor = ArgumentCaptor.forClass(RunbookExecution.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        List<RunbookExecution> saves = captor.getAllValues();
        assertThat(saves.get(0).status()).isEqualTo("RUNNING");
        assertThat(saves.get(saves.size() - 1).status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("SCRIPT 白名单内命令退出码 0 → SUCCESS（进程桩化）")
    void should_runScript_when_allowedAndExitZero() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(true);
        when(process.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("hello".getBytes()));
        when(process.exitValue()).thenReturn(0);
        RunbookExecutor scriptExecutor = new RunbookExecutor(repository, chatService, httpClient,
                null, true, List.of("echo"), 1) {
            @Override
            Process createProcess(String command) {
                return process;
            }
        };
        Runbook rb = new Runbook(5L, "脚本", "d", "K",
                List.of("SCRIPT echo hello"), 1, null, true);

        RunbookExecution result = scriptExecutor.execute(rb);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.stepResults().get(0).output()).contains("exit=0").contains("hello");
    }

    @Test
    @DisplayName("SCRIPT 命令退出码非 0 → FAILED")
    void should_failScript_when_exitNonZero() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(true);
        when(process.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));
        when(process.exitValue()).thenReturn(2);
        RunbookExecutor scriptExecutor = new RunbookExecutor(repository, chatService, httpClient,
                null, true, List.of("echo"), 1) {
            @Override
            Process createProcess(String command) {
                return process;
            }
        };
        Runbook rb = new Runbook(5L, "脚本", "d", "K",
                List.of("SCRIPT echo x"), 1, null, true);

        RunbookExecution result = scriptExecutor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults().get(0).output()).contains("exit=2");
    }

    @Test
    @DisplayName("SCRIPT 执行超时 → 强制销毁并 FAILED")
    void should_failScript_when_timeout() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(false);
        RunbookExecutor scriptExecutor = new RunbookExecutor(repository, chatService, httpClient,
                null, true, List.of("echo"), 1) {
            @Override
            Process createProcess(String command) {
                return process;
            }
        };
        Runbook rb = new Runbook(5L, "脚本", "d", "K",
                List.of("SCRIPT echo x"), 1, null, true);

        RunbookExecution result = scriptExecutor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults().get(0).output()).contains("超时");
        verify(process).destroyForcibly();
    }

    @Test
    @DisplayName("WEBHOOK 步骤缺省 URL 走默认值（默认 URL 被 SSRF 拦截）")
    void should_useDefaultUrl_when_webhookStepIncomplete() {
        Runbook rb = new Runbook(5L, "默认", "d", "K",
                List.of("WEBHOOK "), 1, null, true);

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults().get(0).output()).contains("内网");
    }

    @Test
    @DisplayName("回滚步骤中的空行被跳过")
    void should_skipBlankLines_when_rollbackContainsEmpty() {
        Runbook rb = new Runbook(5L, "发布", "d", "K",
                List.of("发布新版本"), 4, "回退\n\n通知", true);
        when(chatService.chatWithSystemPrompt(any(), any()))
                .thenThrow(new RuntimeException("发布失败"))
                .thenReturn("回退完成")
                .thenReturn("通知完成");

        RunbookExecution result = executor.execute(rb);

        assertThat(result.stepResults()).hasSize(3);
        assertThat(result.stepResults().get(1).command()).startsWith("ROLLBACK: 回退");
        assertThat(result.stepResults().get(2).command()).startsWith("ROLLBACK: 通知");
    }

    @Test
    @DisplayName("领域事件发布异常不影响执行结果")
    void should_toleratePublisherFailure_when_eventBusDown() {
        org.mockito.Mockito.doThrow(new RuntimeException("bus down"))
                .when(eventPublisher).publishEvent(any());
        Runbook rb = new Runbook(5L, "巡检", "d", "K",
                List.of("检查磁盘"), 1, null, true);
        when(chatService.chatWithSystemPrompt(any(), any())).thenReturn("ok");

        assertThat(executor.execute(rb).status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("步骤异常消息为 null 时回退到 toString")
    void should_useToString_when_exceptionMessageNull() {
        Runbook rb = new Runbook(5L, "巡检", "d", "K",
                List.of("检查磁盘"), 1, null, true);
        when(chatService.chatWithSystemPrompt(any(), any()))
                .thenThrow(new RuntimeException());

        RunbookExecution result = executor.execute(rb);

        assertThat(result.stepResults().get(0).status()).isEqualTo("FAILED");
        assertThat(result.stepResults().get(0).output()).contains("RuntimeException");
    }

    @Test
    @DisplayName("HTTP 步骤仅给方法缺省 URL（POST + 默认 URL 被 SSRF 拦截）")
    void should_useDefaultUrl_when_httpMethodOnly() {
        Runbook rb = new Runbook(5L, "默认", "d", "K",
                List.of("HTTP POST"), 1, null, true);

        RunbookExecution result = executor.execute(rb);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.stepResults().get(0).output()).contains("内网");
    }

    @Test
    @DisplayName("执行记录 id 为 null 时事件载荷 executionId 以 0 兜底")
    void should_publishWithZeroExecutionId_when_idNull() {
        org.mockito.Mockito.doAnswer(inv -> inv.getArgument(0)).when(repository).save(any());
        Runbook rb = new Runbook(5L, "巡检", "d", "K",
                List.of("检查磁盘"), 1, null, true);
        when(chatService.chatWithSystemPrompt(any(), any())).thenReturn("ok");

        executor.execute(rb);

        ArgumentCaptor<com.smartops.domain.event.OpsEvent> captor =
                ArgumentCaptor.forClass(com.smartops.domain.event.OpsEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().payload())
                .containsEntry("executionId", 0L);
    }

    @Test
    @DisplayName("执行成功发布 RUNBOOK_COMPLETED 领域事件")
    void should_publishCompleted_when_success() {
        Runbook rb = new Runbook(5L, "巡检", "d", "K",
                List.of("检查磁盘"), 1, null, true);
        when(chatService.chatWithSystemPrompt(any(), any())).thenReturn("ok");

        executor.execute(rb);

        ArgumentCaptor<com.smartops.domain.event.OpsEvent> captor =
                ArgumentCaptor.forClass(com.smartops.domain.event.OpsEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type())
                .isEqualTo(com.smartops.domain.event.OpsEvent.RUNBOOK_COMPLETED);
        assertThat(captor.getValue().payload()).containsEntry("runbook", "巡检");
    }

    @Test
    @DisplayName("执行失败发布 RUNBOOK_FAILED 领域事件")
    void should_publishFailed_when_failure() {
        Runbook rb = new Runbook(5L, "巡检", "d", "K",
                List.of("检查磁盘"), 1, null, true);
        when(chatService.chatWithSystemPrompt(any(), any()))
                .thenThrow(new RuntimeException("LLM 不可用"));

        executor.execute(rb);

        ArgumentCaptor<com.smartops.domain.event.OpsEvent> captor =
                ArgumentCaptor.forClass(com.smartops.domain.event.OpsEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type())
                .isEqualTo(com.smartops.domain.event.OpsEvent.RUNBOOK_FAILED);
    }

    @Test
    @DisplayName("发布器为 null 时执行不受影响")
    void should_executeNormally_when_publisherNull() {
        RunbookExecutor noPublisher = new RunbookExecutor(repository, chatService, httpClient,
                null, false, List.of(), 1);
        Runbook rb = new Runbook(5L, "巡检", "d", "K",
                List.of("检查磁盘"), 1, null, true);
        when(chatService.chatWithSystemPrompt(any(), any())).thenReturn("ok");

        assertThat(noPublisher.execute(rb).status()).isEqualTo("SUCCESS");
    }
}
