package com.smartops.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatRequest} DTO 测试。
 *
 * <p>验证对话请求的校验规则：消息非空、长度限制、会话 ID 可选。
 * 对应 agent.md 第六章 6.3 节测试编写规范。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class ChatRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    @DisplayName("合法请求（有会话 ID 和消息）校验通过")
    void should_passValidation_when_validRequestWithConversationId() {
        ChatRequest request = new ChatRequest("conv-1", "查询 CPU 使用率", null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("合法请求（无会话 ID，有消息）校验通过")
    void should_passValidation_when_validRequestWithoutConversationId() {
        ChatRequest request = new ChatRequest(null, "查询内存使用率", null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("消息为 null 时校验失败")
    void should_failValidation_when_messageIsNull() {
        ChatRequest request = new ChatRequest("conv-1", null, null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("不能为空");
    }

    @Test
    @DisplayName("消息为空字符串时校验失败")
    void should_failValidation_when_messageIsBlank() {
        ChatRequest request = new ChatRequest("conv-1", "", null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("消息超过 2000 字符时校验失败")
    void should_failValidation_when_messageExceedsMaxLength() {
        String longMessage = "a".repeat(2001);

        ChatRequest request = new ChatRequest("conv-1", longMessage, null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("2000");
    }

    @Test
    @DisplayName("消息恰好 2000 字符时校验通过")
    void should_passValidation_when_messageIsExactlyMaxLength() {
        String maxMessage = "a".repeat(2000);

        ChatRequest request = new ChatRequest(null, maxMessage, null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("会话 ID 为空白字符串时校验通过（空白视为有效）")
    void should_passValidation_when_conversationIdIsBlank() {
        ChatRequest request = new ChatRequest("   ", "查询 QPS", null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("record 的访问器方法返回正确的值")
    void should_returnCorrectValues_when_accessorsCalled() {
        ChatRequest request = new ChatRequest("conv-123", "查询磁盘", null);

        assertThat(request.conversationId()).isEqualTo("conv-123");
        assertThat(request.message()).isEqualTo("查询磁盘");
        assertThat(request.confirmationToken()).isNull();
    }

    @Test
    @DisplayName("携带确认令牌的请求校验通过且令牌可读取")
    void should_passValidation_when_confirmationTokenProvided() {
        ChatRequest request = new ChatRequest("conv-1", "重启订单服务", "token-abc");

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(request.confirmationToken()).isEqualTo("token-abc");
    }
}
