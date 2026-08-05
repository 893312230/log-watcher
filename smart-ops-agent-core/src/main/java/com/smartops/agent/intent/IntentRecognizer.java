package com.smartops.agent.intent;

import com.smartops.common.model.IntentResult;

/**
 * 意图识别器接口。
 *
 * <p>四层意图识别体系（L1-L4）的统一抽象。每层识别器接收用户输入文本，
 * 返回一个 {@link IntentResult}。若当前层无法识别（低置信度），
 * 由 Pipeline 将输入传递给下一层。</p>
 *
 * <p>所有实现类必须线程安全，因为可能被并发调用。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
public interface IntentRecognizer {

    /**
     * 识别用户输入的意图。
     *
     * @param userInput 用户输入的原始文本，不能为 null 或空白
     * @return 意图识别结果，包含意图类型、置信度和来源层级
     * @throws IllegalArgumentException 当输入为 null 或空白时
     */
    IntentResult recognize(String userInput);

    /**
     * 获取当前识别器的层级标识（L1/L2/L3/L4）。
     *
     * @return 层级标识字符串
     */
    String getLayer();
}
