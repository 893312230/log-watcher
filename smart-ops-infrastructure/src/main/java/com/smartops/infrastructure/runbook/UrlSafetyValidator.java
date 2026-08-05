package com.smartops.infrastructure.runbook;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * URL 安全校验器（SSRF 防护）。
 *
 * <p>仅允许 http/https；主机经 DNS 解析为 {@link InetAddress} 后判定，
 * 拒绝回环、内网（10/8、172.16/12、192.168/16）、链路本地（169.254/16，
 * 含云元数据地址）、未指定地址（0.0.0.0/::）与组播地址。
 * 基于解析结果判定可覆盖十进制/十六进制 IP 字面量与
 * 指向内网的域名（DNS 重绑定在解析时刻被拦截）；解析失败一律拒绝。</p>
 *
 * <p>供 Runbook 控制器与执行引擎、Webhook 控制器共用。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public final class UrlSafetyValidator {

    private UrlSafetyValidator() {
    }

    /**
     * 校验 URL 安全性。
     *
     * @param raw 原始 URL
     * @return 校验通过的 URL
     * @throws IllegalArgumentException 协议非法、地址无效、无法解析或为内网地址时
     */
    public static String validate(String raw) {
        URI uri = URI.create(raw);
        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("仅支持 http/https 协议: " + scheme);
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("无效的 URL: " + raw);
        }
        final InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析的主机: " + host);
        }
        if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            throw new IllegalArgumentException("不允许访问内网地址: " + host);
        }
        return raw;
    }
}
