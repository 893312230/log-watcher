package com.smartops.agent.security;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 一次性人工确认令牌存储。
 *
 * <p>最小安全门的配套组件（P0-3）：高危操作被 {@link SecurityGate} 拦截后，
 * API 层签发令牌返回给客户端；客户端携带令牌与原始消息重提请求，
 * 验证通过后令牌立即被消费（一次性），防止重放。</p>
 *
 * <p><b>安全属性</b>：
 * <ul>
 *   <li>令牌绑定会话 ID 与触发消息：换会话或改消息均验证失败</li>
 *   <li>TTL 10 分钟，过期即失效</li>
 *   <li>一次性：验证成功即删除，不可复用</li>
 *   <li>容量有界（默认 10000 条）：超出时淘汰最旧条目，防内存膨胀</li>
 * </ul></p>
 *
 * <p>单机内存实现，重启后令牌全部失效（客户端重新触发确认流程即可）。
 * 多实例部署需替换为 Redis 共享存储（阶段五）。</p>
 *
 * <p>线程安全：内部方法均 synchronized。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class ConfirmationTokenStore {

    /** 令牌 TTL：10 分钟。 */
    public static final long DEFAULT_TTL_MILLIS = 10 * 60 * 1000L;

    /** 令牌容量上限，超出后淘汰最旧条目。 */
    private static final int MAX_ENTRIES = 10_000;

    private final long ttlMillis;

    /** 令牌 → 待确认操作。LinkedHashMap 保持插入序，用于容量淘汰。 */
    private final Map<String, PendingConfirmation> tokens = new LinkedHashMap<>();

    /**
     * 待确认操作记录。
     *
     * @param conversationId    会话 ID
     * @param triggerMessage    触发确认流程的原始用户消息
     * @param expiryEpochMillis 过期时间（epoch 毫秒）
     */
    private record PendingConfirmation(String conversationId, String triggerMessage,
                                       long expiryEpochMillis) {
    }

    /**
     * 构造默认 TTL（10 分钟）的令牌存储。
     */
    public ConfirmationTokenStore() {
        this(DEFAULT_TTL_MILLIS);
    }

    /**
     * 构造指定 TTL 的令牌存储（供测试缩短 TTL 验证过期行为）。
     *
     * @param ttlMillis 令牌有效期（毫秒），必须为正数
     */
    ConfirmationTokenStore(long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("TTL 必须为正数: " + ttlMillis);
        }
        this.ttlMillis = ttlMillis;
    }

    /**
     * 为指定会话与触发消息签发一次性确认令牌。
     *
     * @param conversationId 会话 ID
     * @param triggerMessage 触发确认流程的原始用户消息
     * @return 签发的令牌
     */
    public synchronized String issue(String conversationId, String triggerMessage) {
        purgeExpired();
        if (tokens.size() >= MAX_ENTRIES) {
            Iterator<String> it = tokens.keySet().iterator();
            it.next();
            it.remove();
        }
        String token = UUID.randomUUID().toString();
        tokens.put(token, new PendingConfirmation(
                conversationId, triggerMessage, System.currentTimeMillis() + ttlMillis));
        return token;
    }

    /**
     * 验证并消费令牌：令牌存在、未过期、且会话 ID 与触发消息均匹配时，
     * 删除令牌并返回 true；任何一项不满足返回 false。
     *
     * @param token          客户端携带的令牌，可为 null
     * @param conversationId 当前请求的会话 ID
     * @param triggerMessage 当前请求的用户消息
     * @return 验证通过返回 true
     */
    public synchronized boolean validateAndConsume(String token, String conversationId,
                                                   String triggerMessage) {
        if (token == null || token.isBlank()) {
            return false;
        }
        PendingConfirmation pending = tokens.get(token);
        if (pending == null) {
            return false;
        }
        if (System.currentTimeMillis() > pending.expiryEpochMillis()) {
            tokens.remove(token);
            return false;
        }
        if (!pending.conversationId().equals(conversationId)
                || !pending.triggerMessage().equals(triggerMessage)) {
            return false;
        }
        tokens.remove(token);
        return true;
    }

    /**
     * 当前令牌数量。
     *
     * @return 令牌数量
     */
    public synchronized int size() {
        return tokens.size();
    }

    /**
     * 清理已过期令牌。
     */
    private void purgeExpired() {
        long now = System.currentTimeMillis();
        tokens.values().removeIf(p -> now > p.expiryEpochMillis());
    }
}
