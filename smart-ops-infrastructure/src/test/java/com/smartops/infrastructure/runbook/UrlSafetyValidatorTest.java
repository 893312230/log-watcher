package com.smartops.infrastructure.runbook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UrlSafetyValidator} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class UrlSafetyValidatorTest {

    @Test
    @DisplayName("合法 http/https 地址校验通过")
    void should_pass_when_publicHttpUrl() {
        assertThat(UrlSafetyValidator.validate("https://example.com/api"))
                .isEqualTo("https://example.com/api");
        assertThat(UrlSafetyValidator.validate("http://8.8.8.8/health"))
                .isEqualTo("http://8.8.8.8/health");
    }

    @Test
    @DisplayName("非 http/https 协议被拒绝")
    void should_reject_when_schemeNotHttp() {
        assertThatThrownBy(() -> UrlSafetyValidator.validate("ftp://example.com/f"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 http/https");
    }

    @Test
    @DisplayName("无主机名的 URL 被拒绝")
    void should_reject_when_hostMissing() {
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("回环与 localhost 被拒绝")
    void should_reject_when_loopback() {
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://localhost:8080/api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内网");
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://127.0.0.1/api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内网");
    }

    @Test
    @DisplayName("内网网段与云元数据地址被拒绝")
    void should_reject_when_internalNetwork() {
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://10.0.0.5/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://172.16.1.1/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://192.168.1.1/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("172.17-172.31 段同样被拒绝（前缀匹配时代的漏网段）")
    void should_reject_when_full172Range() {
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://172.17.0.1/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内网");
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://172.31.255.254/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内网");
    }

    @Test
    @DisplayName("IPv6 回环与未指定地址被拒绝")
    void should_reject_when_ipv6LoopbackOrAny() {
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://[::1]/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://0.0.0.0/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("十进制/十六进制 IP 字面量被解析后拦截或拒绝")
    void should_reject_when_numericIpLiteral() {
        // 127.1 是 127.0.0.1 的合法缩写，解析后为回环
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://127.1/"))
                .isInstanceOf(IllegalArgumentException.class);
        // 0x7f000001 无法作为主机解析 → 拒绝（无法解析即拒绝，不降级放行）
        assertThatThrownBy(() -> UrlSafetyValidator.validate("http://0x7f000001/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("无法解析的主机被拒绝")
    void should_reject_when_hostUnresolvable() {
        assertThatThrownBy(() -> UrlSafetyValidator.validate(
                "http://nonexistent-host.invalid.example/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法解析");
    }
}
