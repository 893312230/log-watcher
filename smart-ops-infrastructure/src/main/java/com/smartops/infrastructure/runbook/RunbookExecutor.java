package com.smartops.infrastructure.runbook;

import com.smartops.domain.event.OpsEvent;
import com.smartops.domain.runbook.Runbook;
import com.smartops.domain.runbook.RunbookExecution;
import com.smartops.domain.runbook.StepResult;
import com.smartops.domain.runbook.port.RunbookExecutionRepository;
import com.smartops.infrastructure.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runbook 执行引擎（阶段十二真实化）。
 *
 * <p>步骤类型由指令前缀决定：{@code HTTP }/{@code WEBHOOK }/{@code SCRIPT }，
 * 其余按 LLM 步骤处理。语义：
 * <ul>
 *   <li>顺序执行，失败即停（后续步骤标记 SKIPPED）</li>
 *   <li>HTTP/WEBHOOK 步骤失败按 {@code smartops.runbook.step-retries} 重试</li>
 *   <li>SCRIPT 默认禁用（{@code smartops.runbook.script-enabled=false}），
 *       启用后仅允许 {@code smartops.runbook.script-allowlist} 前缀命令</li>
 *   <li>执行失败且配置回滚步骤时，依次执行回滚步骤（指令加 ROLLBACK 前缀记录）</li>
 *   <li>全程边执行边落库（RUNNING → SUCCESS/FAILED + 每步结果）</li>
 * </ul></p>
 *
 * <p>线程安全：无内部状态，依赖组件线程安全。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class RunbookExecutor {

    private static final Logger log = LoggerFactory.getLogger(RunbookExecutor.class);

    /** 单步输出最大长度（防止大响应撑爆数据库）。 */
    private static final int OUTPUT_MAX_LENGTH = 4000;

    /** SCRIPT 步骤超时（秒）。 */
    private static final int SCRIPT_TIMEOUT_SECONDS = 30;

    private final RunbookExecutionRepository executionRepository;
    private final ChatService chatService;
    private final HttpClient httpClient;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean scriptEnabled;
    private final List<String> scriptAllowlist;
    private final int stepRetries;

    /**
     * 构造执行引擎（Spring 装配入口）。
     *
     * @param executionRepository 执行历史仓库
     * @param chatService         LLM 调用通道
     * @param eventPublisher      领域事件发布器
     * @param scriptEnabled       是否允许 SCRIPT 步骤
     * @param scriptAllowlist     SCRIPT 命令白名单前缀
     * @param stepRetries         HTTP/WEBHOOK 步骤失败重试次数
     */
    @org.springframework.beans.factory.annotation.Autowired
    public RunbookExecutor(RunbookExecutionRepository executionRepository,
                           ChatService chatService,
                           ApplicationEventPublisher eventPublisher,
                           @Value("${smartops.runbook.script-enabled:false}") boolean scriptEnabled,
                           @Value("${smartops.runbook.script-allowlist:}") List<String> scriptAllowlist,
                           @Value("${smartops.runbook.step-retries:1}") int stepRetries) {
        this(executionRepository, chatService,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                eventPublisher, scriptEnabled, scriptAllowlist, stepRetries);
    }

    /**
     * 构造执行引擎（测试用，注入 HttpClient）。
     *
     * @param executionRepository 执行历史仓库
     * @param chatService         LLM 调用通道
     * @param httpClient          HTTP 客户端
     * @param eventPublisher      领域事件发布器（可为 null，null 时不发布事件）
     * @param scriptEnabled       是否允许 SCRIPT 步骤
     * @param scriptAllowlist     SCRIPT 命令白名单前缀
     * @param stepRetries         失败重试次数
     */
    public RunbookExecutor(RunbookExecutionRepository executionRepository,
                           ChatService chatService, HttpClient httpClient,
                           ApplicationEventPublisher eventPublisher,
                           boolean scriptEnabled, List<String> scriptAllowlist, int stepRetries) {
        this.executionRepository = executionRepository;
        this.chatService = chatService;
        this.httpClient = httpClient;
        this.eventPublisher = eventPublisher;
        this.scriptEnabled = scriptEnabled;
        this.scriptAllowlist = scriptAllowlist;
        this.stepRetries = stepRetries;
    }

    /**
     * 执行 Runbook 并落库执行历史。
     *
     * @param runbook Runbook 定义
     * @return 终态执行记录（SUCCESS / FAILED）
     */
    public RunbookExecution execute(Runbook runbook) {
        RunbookExecution running = executionRepository.save(
                RunbookExecution.start(runbook.id(), Instant.now()));
        return execute(runbook, running);
    }

    /**
     * 在已落库的 RUNNING 记录上执行（异步入口复用同一执行记录，避免重复建档）。
     *
     * @param runbook Runbook 定义
     * @param running 已落库的 RUNNING 执行记录
     * @return 终态执行记录（SUCCESS / FAILED）
     */
    public RunbookExecution execute(Runbook runbook, RunbookExecution running) {
        List<StepResult> results = new ArrayList<>();
        boolean failed = false;
        List<String> steps = runbook.steps();
        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);
            if (failed) {
                results.add(new StepResult(i + 1, step, "SKIPPED", "前序步骤失败，跳过"));
                continue;
            }
            StepResult result = executeWithRetry(i, step);
            results.add(result);
            if (!"SUCCESS".equals(result.status())) {
                failed = true;
            }
        }

        if (failed) {
            results.addAll(executeRollback(runbook, steps.size()));
        }

        RunbookExecution finished = running.finish(failed ? "FAILED" : "SUCCESS", results, Instant.now());
        RunbookExecution saved = executionRepository.save(finished);
        publishOutcome(runbook, saved);
        return saved;
    }

    /** 执行结束后发布领域事件（发布器缺省或异常均不影响执行结果）。 */
    private void publishOutcome(Runbook runbook, RunbookExecution finished) {
        if (eventPublisher == null) {
            return;
        }
        try {
            String type = "FAILED".equals(finished.status())
                    ? OpsEvent.RUNBOOK_FAILED : OpsEvent.RUNBOOK_COMPLETED;
            eventPublisher.publishEvent(OpsEvent.of(type, Map.of(
                    "runbookId", runbook.id() == null ? 0 : runbook.id(),
                    "runbook", runbook.name(),
                    "executionId", finished.id() == null ? 0 : finished.id(),
                    "status", finished.status())));
        } catch (RuntimeException e) {
            log.warn("Runbook 领域事件发布失败（不影响执行结果）: {}", e.toString());
        }
    }

    private StepResult executeWithRetry(int index, String step) {
        int attempts = isRetryable(step) ? stepRetries + 1 : 1;
        StepResult last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            last = executeStep(index, step);
            if ("SUCCESS".equals(last.status())) {
                return last;
            }
            log.warn("步骤失败（第 {}/{} 次）: {}", attempt, attempts, step);
        }
        return last;
    }

    private static boolean isRetryable(String step) {
        return step.startsWith("HTTP ") || step.startsWith("WEBHOOK ");
    }

    private StepResult executeStep(int index, String step) {
        try {
            if (step.startsWith("HTTP ")) {
                return executeHttp(index, step);
            }
            if (step.startsWith("WEBHOOK ")) {
                return executeWebhook(index, step);
            }
            if (step.startsWith("SCRIPT ")) {
                return executeScript(index, step);
            }
            return executeLlm(index, step);
        } catch (Exception e) {
            log.warn("步骤执行异常: {}", step, e);
            return new StepResult(index + 1, step, "FAILED",
                    truncate(e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    private StepResult executeHttp(int index, String step) throws Exception {
        String[] parts = step.split("\\s+", 3);
        String method = parts.length > 1 ? parts[1].toUpperCase() : "GET";
        String rawUrl = parts.length > 2 ? parts[2] : "http://localhost:8080/api/health";
        String url = UrlSafetyValidator.validate(rawUrl);
        var builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(5));
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString("{}"));
        }
        HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        boolean success = resp.statusCode() >= 200 && resp.statusCode() < 300;
        return new StepResult(index + 1, step, success ? "SUCCESS" : "FAILED",
                method + " " + url + " → " + resp.statusCode());
    }

    private StepResult executeWebhook(int index, String step) throws Exception {
        String[] parts = step.split("\\s+", 3);
        String url = UrlSafetyValidator.validate(
                parts.length > 2 ? parts[2] : "http://localhost:8080/api/health");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .timeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return new StepResult(index + 1, step, resp.statusCode() < 400 ? "SUCCESS" : "FAILED",
                "POST " + url + " → " + resp.statusCode());
    }

    private StepResult executeScript(int index, String step) throws Exception {
        String command = step.substring("SCRIPT ".length()).trim();
        if (!scriptEnabled) {
            return new StepResult(index + 1, step, "FAILED",
                    "SCRIPT 步骤未启用（smartops.runbook.script-enabled=false）");
        }
        boolean allowed = scriptAllowlist.stream().anyMatch(command::startsWith);
        if (!allowed) {
            return new StepResult(index + 1, step, "FAILED", "命令不在白名单内");
        }
        Process process = createProcess(command);
        boolean done = process.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!done) {
            process.destroyForcibly();
            return new StepResult(index + 1, step, "FAILED", "脚本执行超时");
        }
        String output = truncate(new String(process.getInputStream().readAllBytes()));
        int exit = process.exitValue();
        return new StepResult(index + 1, step, exit == 0 ? "SUCCESS" : "FAILED",
                "exit=" + exit + " " + output);
    }

    /**
     * 创建脚本进程（独立方法便于测试替换，不依赖真实 shell）。
     *
     * @param command 白名单校验通过的命令
     * @return 已启动的进程
     * @throws java.io.IOException 进程启动失败
     */
    Process createProcess(String command) throws java.io.IOException {
        return new ProcessBuilder("cmd", "/c", command).redirectErrorStream(true).start();
    }

    private StepResult executeLlm(int index, String step) {
        String output = chatService.chatWithSystemPrompt(
                "你是运维执行引擎，请根据操作步骤输出执行结果。只读操作模拟输出，写操作标注需人工确认。",
                step);
        return new StepResult(index + 1, step, "SUCCESS", truncate(output));
    }

    private List<StepResult> executeRollback(Runbook runbook, int baseSeq) {
        String rollback = runbook.rollbackSteps();
        if (rollback == null || rollback.isBlank()) {
            return List.of();
        }
        log.info("执行失败，开始回滚: runbook={}", runbook.name());
        List<StepResult> results = new ArrayList<>();
        String[] lines = rollback.split("\\R");
        int seq = baseSeq;
        for (String line : lines) {
            String step = line.trim();
            if (step.isEmpty()) {
                continue;
            }
            seq++;
            StepResult result = executeStep(seq - 1, step);
            results.add(new StepResult(seq, "ROLLBACK: " + result.command(),
                    result.status(), result.output()));
        }
        return results;
    }

    private static String truncate(String text) {
        return text.length() <= OUTPUT_MAX_LENGTH ? text : text.substring(0, OUTPUT_MAX_LENGTH);
    }
}
