package com.smartops.api.config;

import com.smartops.common.exception.LlmCallException;
import com.smartops.common.exception.RateLimitException;
import com.smartops.common.exception.SecurityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * 全局异常处理器（阶段九可靠性加固）。
 *
 * <p>统一将异常映射为结构化 JSON 错误响应，
 * 替代 Spring Boot 默认的 BasicErrorController 格式。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LlmCallException.class)
    public ResponseEntity<Map<String, Object>> handleLlmCall(LlmCallException e) {
        log.warn("LLM 调用异常: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "LLM_CALL_FAILED", e.getMessage());
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitException e) {
        log.warn("限流触发: {}", e.getMessage());
        return error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", e.getMessage());
    }

    @ExceptionHandler(SecurityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(SecurityViolationException e) {
        log.warn("安全违规: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, "SECURITY_VIOLATION", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArg(IllegalArgumentException e) {
        log.debug("请求参数非法: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    /**
     * 保留 {@code ResponseStatusException} 的原始状态码（如 404），
     * 避免被通用 Exception 处理器吞成 500。
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            org.springframework.web.server.ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        return error(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR,
                String.valueOf(e.getStatusCode().value()),
                e.getReason() != null ? e.getReason() : "请求错误");
    }

    /**
     * 请求体 JSON 反序列化失败（类型不匹配/格式错误）→ 400，
     * 避免落入通用 Exception 处理器被报成 500。
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.debug("请求体解析失败: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "请求体格式错误");
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            org.springframework.web.bind.MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a,b) -> a + "; " + b).orElse("参数校验失败");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", msg);
    }

    /**
     * 数据库约束冲突（如删除仍有执行历史的 Runbook 触发外键）→ 409，
     * 避免落入通用 Exception 处理器被报成 500。
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("数据约束冲突: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "CONFLICT", "操作与现有数据冲突（存在关联记录）");
    }

    /**
     * 静态资源未找到（绝大多数为公网扫描器探测）→ 404，INFO 单行记录。
     * 避免落入通用处理器以 ERROR 全堆栈输出——ERROR 日志会被 logwatch
     * 采集形成告警噪音（生产实测占告警量 90%）；INFO 级在 ML 直通模式下
     * 经 ML 定级判 INFO 抑制，不产生告警。
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException e) {
        log.info("资源不存在: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("未处理异常", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "服务器内部错误，请查看日志");
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", code,
                "message", message,
                "timestamp", Instant.now().toString()));
    }
}
