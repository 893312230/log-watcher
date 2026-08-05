package com.smartops.api.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JwtService} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-0123456789abcdef01";

    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    @DisplayName("签发后可校验并解析用户名与角色")
    void should_roundTripClaims() {
        String token = jwtService.issue("admin", "ADMIN");

        Optional<Claims> claims = jwtService.validate(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("admin");
        assertThat(claims.get().get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("篡改或错误密钥签名的令牌校验失败")
    void should_rejectTamperedToken() {
        String token = jwtService.issue("admin", "ADMIN");
        assertThat(jwtService.validate(token + "x")).isEmpty();

        JwtService other = new JwtService("other-secret-key-0123456789abcdef");
        assertThat(other.validate(token)).isEmpty();
    }

    @Test
    @DisplayName("空令牌校验返回空")
    void should_returnEmpty_when_tokenBlank() {
        assertThat(jwtService.validate(null)).isEmpty();
        assertThat(jwtService.validate("  ")).isEmpty();
    }

    @Test
    @DisplayName("密钥不足 32 字节时构造失败")
    void should_fail_when_secretTooShort() {
        assertThatThrownBy(() -> new JwtService("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }
}
