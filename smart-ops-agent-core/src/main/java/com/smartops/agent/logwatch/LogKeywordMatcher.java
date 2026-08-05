package com.smartops.agent.logwatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 日志关键字匹配器（采集预过滤）。
 *
 * <p>决定一条日志事件是否值得进入分析队列。规则：</p>
 * <ul>
 *   <li>内置错误关键字（ERROR/Exception/FATAL/OutOfMemory 等）恒生效，
 *       保证"有报错必提醒"的底线</li>
 *   <li>用户自定义关键字（yml 配置）叠加生效，覆盖业务级关注点
 *       （如"余额不足""库存锁定失败"）</li>
 *   <li>排除子串（yml 配置）优先判定：命中即放行不告警，
 *       用于屏蔽采集管道自身日志造成的自引用告警风暴</li>
 *   <li>子串匹配，大小写不敏感</li>
 * </ul>
 *
 * <p>线程安全：构造后不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public class LogKeywordMatcher {

    /** 内置错误关键字（原文形式，命中时作为 matchedKeyword 返回）。 */
    private static final List<String> BUILTIN_KEYWORDS = List.of(
            "ERROR", "Exception", "FATAL", "OutOfMemory", "StackOverflow");

    private final List<String> customKeywords;
    private final List<String> excludeKeywords;

    /**
     * 构造关键字匹配器（无排除子串）。
     *
     * @param customKeywords 用户自定义关键字，可为 null（按空列表处理）
     */
    public LogKeywordMatcher(List<String> customKeywords) {
        this(customKeywords, null);
    }

    /**
     * 构造关键字匹配器。
     *
     * @param customKeywords  用户自定义关键字，可为 null（按空列表处理）
     * @param excludeKeywords 排除子串（命中即不告警），可为 null（按空列表处理）
     */
    public LogKeywordMatcher(List<String> customKeywords, List<String> excludeKeywords) {
        this.customKeywords = customKeywords == null
                ? List.of()
                : List.copyOf(customKeywords);
        this.excludeKeywords = excludeKeywords == null
                ? List.of()
                : List.copyOf(excludeKeywords);
    }

    /**
     * 匹配日志内容。
     *
     * @param content 日志内容（已合并多行）
     * @return 命中的关键字（原文形式）；未命中或命中排除子串返回 empty
     */
    public Optional<String> match(String content) {
        if (isExcluded(content)) {
            return Optional.empty();
        }
        String lower = content.toLowerCase(Locale.ROOT);
        for (String keyword : allKeywords()) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return Optional.of(keyword);
            }
        }
        return Optional.empty();
    }

    /**
     * 是否命中排除子串（阶段十五：ML 模式下预过滤放开关键字、仅保留排除判定）。
     *
     * @param content 日志内容
     * @return 命中任一排除子串（大小写不敏感）返回 true
     */
    public boolean isExcluded(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        for (String exclude : excludeKeywords) {
            if (lower.contains(exclude.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 内置 + 自定义的完整关键字列表。
     */
    private List<String> allKeywords() {
        List<String> all = new ArrayList<>(BUILTIN_KEYWORDS.size() + customKeywords.size());
        all.addAll(BUILTIN_KEYWORDS);
        all.addAll(customKeywords);
        return all;
    }
}
