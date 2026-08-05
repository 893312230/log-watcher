package com.smartops.agent.security.impl;

import com.smartops.agent.security.InputFilter;
import com.smartops.common.exception.SecurityViolationException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 基于正则模式的 L0 输入过滤器实现（阶段五安全模型）。
 *
 * <p>阻断高风险注入模式（脚本标签、SQL 注入关键字、命令注入管道符），
 * 拒绝时抛出 SecurityViolationException；无风险输入原样返回。
 * 不依赖外部服务，纯内存操作。</p>
 *
 * <p>线程安全：Pattern 不可变，无内部状态。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class RegexInputFilter implements InputFilter {

    /** 高危模式：命中任一即拒绝，不可清洗。 */
    private static final List<Pattern> BLOCK_PATTERNS = List.of(
            // XSS: script 标签、事件处理器
            Pattern.compile("<script[\\s>]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
            // SQL 注入: 联合查询、注释掉后续
            Pattern.compile("\\bUNION\\s+SELECT\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("'\\s*OR\\s+'1'\\s*=\\s*'1", Pattern.CASE_INSENSITIVE),
            Pattern.compile("--\\s*$", Pattern.MULTILINE),
            // 命令注入: 反引号包围的命令、输出重定向
            Pattern.compile("`[^`]+`"),
            Pattern.compile("\\|.*>")
    );

    /** 安全上限：输入超过此长度拒绝（DoS 防护）。 */
    private static final int MAX_INPUT_LENGTH = 2000;

    @Override
    public String filter(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return "";
        }
        if (rawInput.length() > MAX_INPUT_LENGTH) {
            throw new SecurityViolationException(
                    SecurityViolationException.ERROR_CODE_PREFIX + "INPUT_TOO_LONG",
                    "输入过长，最大允许 " + MAX_INPUT_LENGTH + " 字符");
        }
        for (Pattern pattern : BLOCK_PATTERNS) {
            if (pattern.matcher(rawInput).find()) {
                throw new SecurityViolationException(
                        SecurityViolationException.ERROR_CODE_PREFIX + "INJECTION_DETECTED",
                        "输入包含高危模式: " + pattern.pattern());
            }
        }
        return rawInput;
    }
}
