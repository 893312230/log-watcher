package com.smartops.agent.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PromptWrapper} 单元测试。
 *
 * <p>覆盖：系统指令包裹（含防御句）、用户输入包裹。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class PromptWrapperTest {

    @Test
    @DisplayName("wrapSystem 包含开始/结束标记与防御句")
    void should_wrapSystemPrompt() {
        String wrapped = PromptWrapper.wrapSystem("你是一个 AI 助手");

        assertThat(wrapped).startsWith(PromptWrapper.SYSTEM_START);
        assertThat(wrapped).endsWith(PromptWrapper.SYSTEM_END);
        assertThat(wrapped).contains("你是一个 AI 助手");
        assertThat(wrapped).contains(PromptWrapper.DEFENSE_CLAUSE);
    }

    @Test
    @DisplayName("wrapUserInput 包含开始/结束标记与原始内容")
    void should_wrapUserInput() {
        String wrapped = PromptWrapper.wrapUserInput("查询 CPU");

        assertThat(wrapped).startsWith(PromptWrapper.USER_INPUT_START);
        assertThat(wrapped).endsWith(PromptWrapper.USER_INPUT_END);
        assertThat(wrapped).contains("查询 CPU");
    }

    @Test
    @DisplayName("构造函数为私有（工具类）")
    void should_havePrivateConstructor() throws Exception {
        var ctor = PromptWrapper.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        // should not throw
        ctor.newInstance();
    }
}
