package com.smartops.domain.logwatch;

import com.smartops.common.exception.LogWatchException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;

/**
 * 日志事件。
 *
 * <p>采集源产出的一条逻辑日志（堆栈等多行已合并为单条），
 * 是分析管线的最小输入单元。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param source    采集源标识（日志文件路径或 jar 包路径）
 * @param content   日志正文（可能包含多行堆栈）
 * @param timestamp 采集时间
 */
public record LogEvent(
        String source,
        String content,
        Instant timestamp
) {

    /** 指纹归一化时参与哈希的内容最大长度，截断长堆栈避免噪声。 */
    private static final int FINGERPRINT_CONTENT_MAX_LENGTH = 200;

    /**
     * 紧凑构造器：必填字段非空校验。
     *
     * @param source    采集源标识
     * @param content   日志正文
     * @param timestamp 采集时间
     */
    public LogEvent {
        Objects.requireNonNull(source, "采集源不能为 null");
        Objects.requireNonNull(content, "日志正文不能为 null");
        Objects.requireNonNull(timestamp, "采集时间不能为 null");
    }

    /**
     * 归一化日志正文中的动态部分（ISO 时间戳、UUID、长十六进制、IPv4、数字）
     * 为 {@code #} 占位符并压缩空白。指纹计算与 ML 定级特征化共用，
     * 保证"同类日志仅动态值不同"在两条链路上都被消除。
     *
     * @param content 日志正文
     * @return 归一化后的文本
     */
    public static String normalizeDynamicParts(String content) {
        return content
                .replaceAll("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?", "#")
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "#")
                .replaceAll("\\b[0-9a-fA-F]{12,}\\b", "#")
                .replaceAll("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b", "#")
                .replaceAll("\\d+", "#")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 计算事件指纹：归一化（见 {@link #normalizeDynamicParts(String)}）后
     * 取前 {@value #FINGERPRINT_CONTENT_MAX_LENGTH} 字符做 SHA-256，
     * 使同类日志（仅动态值不同）产生相同指纹。
     *
     * <p>该指纹是 L0 抑制层去重合并、告警表 fingerprint 索引的基础。</p>
     *
     * @return 64 位十六进制指纹字符串
     */
    public String fingerprint() {
        String normalized = normalizeDynamicParts(content);
        if (normalized.length() > FINGERPRINT_CONTENT_MAX_LENGTH) {
            normalized = normalized.substring(0, FINGERPRINT_CONTENT_MAX_LENGTH);
        }
        return sha256Hex(normalized);
    }

    /**
     * 计算字符串的 SHA-256 十六进制摘要。
     *
     * @param text 输入字符串
     * @return 64 位小写十六进制
     */
    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然内置 SHA-256，到达此分支说明运行环境损坏
            throw new LogWatchException("SHA-256 算法不可用", e);
        }
    }
}
