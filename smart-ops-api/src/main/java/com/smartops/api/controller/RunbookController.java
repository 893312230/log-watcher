package com.smartops.api.controller;

import com.smartops.agent.security.ConfirmationTokenStore;
import com.smartops.api.dto.RunbookRequest;
import com.smartops.domain.runbook.Runbook;
import com.smartops.domain.runbook.RunbookExecution;
import com.smartops.domain.runbook.port.RunbookExecutionRepository;
import com.smartops.domain.runbook.port.RunbookRepository;
import com.smartops.infrastructure.runbook.RunbookExecutor;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Runbook 管理 REST 入口（持久化存储，按 id 寻址）。
 *
 * <p>执行语义委托 {@link RunbookExecutor}：顺序执行、失败即停、
 * HTTP/WEBHOOK 重试、SCRIPT 白名单、失败回滚，全程落库。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/runbooks")
public class RunbookController {

    /** 安全等级达到该值时执行需人工确认令牌。 */
    static final int APPROVAL_REQUIRED_LEVEL = 4;

    private static final Logger log = LoggerFactory.getLogger(RunbookController.class);

    private final RunbookRepository runbookRepository;
    private final RunbookExecutionRepository executionRepository;
    private final RunbookExecutor runbookExecutor;
    private final ConfirmationTokenStore confirmationTokenStore;
    private final Executor runbookTaskExecutor;

    /**
     * 构造 Runbook 控制器。
     *
     * @param runbookRepository      Runbook 定义仓库
     * @param executionRepository    执行历史仓库
     * @param runbookExecutor        真实执行引擎
     * @param confirmationTokenStore 高危执行确认令牌存储
     * @param runbookTaskExecutor    Runbook 异步执行线程池
     */
    public RunbookController(RunbookRepository runbookRepository,
                             RunbookExecutionRepository executionRepository,
                             RunbookExecutor runbookExecutor,
                             ConfirmationTokenStore confirmationTokenStore,
                             @Qualifier("runbookTaskExecutor") Executor runbookTaskExecutor) {
        this.runbookRepository = runbookRepository;
        this.executionRepository = executionRepository;
        this.runbookExecutor = runbookExecutor;
        this.confirmationTokenStore = confirmationTokenStore;
        this.runbookTaskExecutor = runbookTaskExecutor;
    }

    /**
     * 分页查询 Runbook。
     *
     * @param page 页码（0 起始，可选）
     * @param size 每页大小（默认 100，上限 500，可选）
     * @return Runbook 列表
     */
    @GetMapping
    public List<Runbook> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return com.smartops.api.support.PageSlice.slice(runbookRepository.findAll(), page, size);
    }

    /**
     * 创建 Runbook。
     *
     * @param request 创建请求（name/description/triggerKeyword/steps/safetyLevel/rollbackSteps/enabled）
     * @return 含分配 id 的 Runbook
     */
    @PostMapping
    public Runbook create(@Valid @RequestBody RunbookRequest request) {
        Runbook rb = new Runbook(null,
                request.name(), request.description(), request.triggerKeyword(),
                request.steps() == null ? List.of() : request.steps(),
                request.safetyLevel() == null ? 1 : request.safetyLevel(),
                request.rollbackSteps(),
                request.enabled() == null || request.enabled());
        return runbookRepository.save(rb);
    }

    /**
     * 异步执行指定 Runbook（顺序执行、失败即停、失败回滚），执行历史落库。
     *
     * <p>安全等级 ≥ {@value #APPROVAL_REQUIRED_LEVEL} 时走审批门：
     * 未携带有效 {@code X-Confirm-Token} 头时签发一次性令牌并返回
     * {@code pendingConfirmation=true}；客户端确认后携带令牌重提方可执行。</p>
     *
     * <p>通过审批门后落库 RUNNING 执行记录并立即返回
     * {@code {executionId, status:"RUNNING"}}；实际执行提交专用线程池，
     * 客户端轮询 {@code GET /api/runbooks/executions/{execId}} 获取终态与步骤结果。</p>
     *
     * @param id           Runbook id
     * @param confirmToken 高危执行确认令牌（可选请求头）
     * @return 异步受理响应（executionId/status/runbook）或待确认响应
     */
    @PostMapping("/{id}/execute")
    public Map<String, Object> execute(@PathVariable long id,
            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken) {
        Runbook rb = runbookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Runbook 不存在: " + id));
        if (rb.safetyLevel() >= APPROVAL_REQUIRED_LEVEL) {
            String conversationId = "runbook:" + id;
            if (!confirmationTokenStore.validateAndConsume(confirmToken, conversationId, rb.name())) {
                String issued = confirmationTokenStore.issue(conversationId, rb.name());
                return Map.of("pendingConfirmation", true, "confirmationToken", issued,
                        "runbook", rb.name(), "safetyLevel", rb.safetyLevel());
            }
        }
        RunbookExecution running = executionRepository.save(
                RunbookExecution.start(rb.id(), Instant.now()));
        runbookTaskExecutor.execute(() -> {
            try {
                runbookExecutor.execute(rb, running);
            } catch (RuntimeException e) {
                log.error("Runbook 异步执行异常 runbook={} executionId={}: {}",
                        rb.name(), running.id(), e.toString());
            }
        });
        return Map.of("executionId", running.id(), "status", "RUNNING", "runbook", rb.name());
    }

    /**
     * 查询单条执行记录（异步执行轮询入口，含步骤结果）。
     *
     * @param execId 执行记录 id
     * @return 执行记录（RUNNING 时 finishedAt 为 null、stepResults 为空）
     */
    @GetMapping("/executions/{execId}")
    public RunbookExecution execution(@PathVariable long execId) {
        return executionRepository.findById(execId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "执行记录不存在: " + execId));
    }

    /**
     * 查询指定 Runbook 的执行历史（开始时间倒序）。
     *
     * @param id Runbook id
     * @return 执行记录列表
     */
    @GetMapping("/{id}/history")
    public List<RunbookExecution> history(@PathVariable long id) {
        return executionRepository.findByRunbookId(id);
    }

    /**
     * 删除指定 Runbook。
     *
     * @param id Runbook id
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        runbookRepository.deleteById(id);
    }
}
