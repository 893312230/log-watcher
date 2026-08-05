package com.smartops.domain.logwatch;

/**
 * 分析层产出。
 *
 * <p>每个分析层执行后返回的裁决，驱动管线流转：
 * SUPPRESS（抑制，终止且不落库）、PROCEED（放行到下一层）、
 * COMPLETE（本层已得出结论，生成告警并终止）。</p>
 *
 * <p>线程安全：record 不可变。</p>
 *
 * @author smartops
 * @since 1.0.0
 *
 * @param verdict 层裁决
 */
public record AnalysisOutcome(Verdict verdict) {

    /**
     * 层裁决枚举。
     */
    public enum Verdict {
        /** 抑制：重复/噪声事件，终止管线且不落库。 */
        SUPPRESS,
        /** 放行：本层未终结，交由下一层继续分析。 */
        PROCEED,
        /** 完成：本层已得出结论，生成告警并终止管线。 */
        COMPLETE
    }

    /**
     * 紧凑构造器：裁决非空校验。
     *
     * @param verdict 层裁决
     */
    public AnalysisOutcome {
        java.util.Objects.requireNonNull(verdict, "层裁决不能为 null");
    }

    /**
     * 构造 SUPPRESS 产出。
     *
     * @return 抑制产出
     */
    public static AnalysisOutcome suppress() {
        return new AnalysisOutcome(Verdict.SUPPRESS);
    }

    /**
     * 构造 PROCEED 产出。
     *
     * @return 放行产出
     */
    public static AnalysisOutcome proceed() {
        return new AnalysisOutcome(Verdict.PROCEED);
    }

    /**
     * 构造 COMPLETE 产出。
     *
     * @return 完成产出
     */
    public static AnalysisOutcome complete() {
        return new AnalysisOutcome(Verdict.COMPLETE);
    }
}
