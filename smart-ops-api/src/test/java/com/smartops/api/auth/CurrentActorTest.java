package com.smartops.api.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CurrentActor} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class CurrentActorTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("无认证上下文时返回 system")
    void should_returnSystem_when_noContext() {
        SecurityContextHolder.clearContext();
        assertThat(CurrentActor.username()).isEqualTo("system");
    }

    @Test
    @DisplayName("匿名用户返回 system")
    void should_returnSystem_when_anonymous() {
        var anonymous = new UsernamePasswordAuthenticationToken(
                "anonymousUser", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        assertThat(CurrentActor.username()).isEqualTo("system");
    }

    @Test
    @DisplayName("已认证用户返回用户名")
    void should_returnUsername_when_authenticated() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(() -> "ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(CurrentActor.username()).isEqualTo("admin");
    }
}
