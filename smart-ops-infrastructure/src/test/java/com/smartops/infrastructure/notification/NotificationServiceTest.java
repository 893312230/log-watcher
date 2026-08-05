package com.smartops.infrastructure.notification;

import com.smartops.common.enums.AlertLevel;
import com.smartops.common.enums.AlertStatus;
import com.smartops.domain.logwatch.Alert;
import com.smartops.infrastructure.persistence.notification.NotificationChannelEntity;
import com.smartops.infrastructure.persistence.notification.NotificationChannelJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationService} 单元测试（mock 仓库与 HttpClient）。
 *
 * @author smartops
 * @since 1.0.0
 */
class NotificationServiceTest {

    private static Alert alert() {
        return new Alert(7L, "fp", "app.log", AlertLevel.ERROR, "ERROR",
                "磁盘已满", "stack", "分析", "建议", 3, 1,
                AlertStatus.OPEN, Instant.now(), Instant.now());
    }

    private static NotificationChannelEntity channel(String url) {
        NotificationChannelEntity e = new NotificationChannelEntity();
        e.setId(1L);
        e.setName("hook");
        e.setType("WEBHOOK");
        e.setTargetUrl(url);
        e.setEnabled(true);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    @Test
    @DisplayName("notify 向全部启用渠道发送请求")
    @SuppressWarnings("unchecked")
    void should_sendToEnabledChannels() throws Exception {
        NotificationChannelJpaRepository repo = mock(NotificationChannelJpaRepository.class);
        HttpClient http = mock(HttpClient.class);
        HttpResponse<Void> resp = mock(HttpResponse.class);
        when(repo.findByEnabledTrue()).thenReturn(List.of(channel("https://example.com/hook")));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        new NotificationService(repo, http).notify(alert());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, timeout(3000)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://example.com/hook");
    }

    @Test
    @DisplayName("notify 无启用渠道时不发送")
    @SuppressWarnings("unchecked")
    void should_skipSend_when_noChannels() throws Exception {
        NotificationChannelJpaRepository repo = mock(NotificationChannelJpaRepository.class);
        HttpClient http = mock(HttpClient.class);
        when(repo.findByEnabledTrue()).thenReturn(List.of());

        new NotificationService(repo, http).notify(alert());

        verify(http, timeout(500).times(0)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("发送异常被吞掉并记录日志")
    @SuppressWarnings("unchecked")
    void should_swallowSendError() throws Exception {
        NotificationChannelJpaRepository repo = mock(NotificationChannelJpaRepository.class);
        HttpClient http = mock(HttpClient.class);
        when(repo.findByEnabledTrue()).thenReturn(List.of(channel("https://example.com/hook")));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("网络错误"));

        new NotificationService(repo, http).notify(alert());

        verify(http, timeout(3000)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
