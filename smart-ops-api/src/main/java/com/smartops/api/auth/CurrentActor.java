package com.smartops.api.auth;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前操作者解析工具（阶段十三审计真实化）。
 *
 * <p>从 SecurityContext 取已认证用户名；无会话上下文（后台线程、
 * 单元测试、匿名访问）时返回 {@code "system"}。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public final class CurrentActor {

    /** 无认证上下文时的兜底 actor。 */
    public static final String SYSTEM = "system";

    private CurrentActor() {
    }

    /**
     * 取当前已认证用户名。
     *
     * @return 用户名，无认证上下文时为 "system"
     */
    public static String username() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || "anonymousUser".equals(auth.getPrincipal())) {
                return SYSTEM;
            }
            return auth.getName();
        } catch (RuntimeException e) {
            return SYSTEM;
        }
    }
}
