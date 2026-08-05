package com.smartops.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test @DisplayName("IllegalArgumentException → 400")
    void should_return400() {
        assertThat(handler.handleBadArg(new IllegalArgumentException("x")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test @DisplayName("Exception → 500")
    void should_return500() {
        assertThat(handler.handleGeneral(new RuntimeException("x")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @Test @DisplayName("HttpMessageNotReadableException → 400")
    void should_return400_onUnreadableBody() {
        assertThat(handler.handleNotReadable(
                new org.springframework.http.converter.HttpMessageNotReadableException(
                        "bad json", (org.springframework.http.HttpInputMessage) null))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test @DisplayName("DataIntegrityViolationException → 409")
    void should_return409_onConstraintViolation() {
        assertThat(handler.handleDataIntegrity(
                new org.springframework.dao.DataIntegrityViolationException("fk"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test @DisplayName("NoResourceFoundException → 404（扫描器探测不升级 500）")
    void should_return404_onNoResource() {
        assertThat(handler.handleNoResource(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET,
                        "/boaform/admin/formLogin", "formLogin"))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
