package com.smartops.agent.logwatch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogWatchProperties} 单元测试（JavaBean 绑定访问器全覆盖）。
 *
 * @author smartops
 * @since 1.0.0
 */
class LogWatchPropertiesTest {

    @Test
    @DisplayName("默认值符合小内存生产安全基线")
    void should_haveSafeDefaults() {
        LogWatchProperties props = new LogWatchProperties();

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getSources()).isEmpty();
        assertThat(props.getExcludeKeywords()).isEmpty();
        assertThat(props.getPollIntervalMs()).isEqualTo(500);
        assertThat(props.getStateDir()).isEqualTo("./logwatch-state");
        assertThat(props.getQueueCapacity()).isEqualTo(2000);
        assertThat(props.getL0().getWindowSeconds()).isEqualTo(300);
        assertThat(props.getL3().getRatePerMinute()).isEqualTo(10);
        assertThat(props.getL3().getEscalateOccurrenceThreshold()).isEqualTo(3);
        assertThat(props.getL4().getDailyLimit()).isEqualTo(20);
        assertThat(props.getSse().getBufferSize()).isEqualTo(256);
    }

    @Test
    @DisplayName("setter/getter 往返一致")
    void should_roundTripAllAccessors() {
        LogWatchProperties props = new LogWatchProperties();
        LogWatchProperties.Source source = new LogWatchProperties.Source();
        source.setType("file");
        source.setPath("/var/log/app.log");
        source.setKeywords(List.of("ERROR"));

        props.setEnabled(true);
        props.setSources(List.of(source));
        props.setExcludeKeywords(List.of("health"));
        props.setPollIntervalMs(1000);
        props.setStateDir("/data/state");
        props.setQueueCapacity(100);

        LogWatchProperties.L0 l0 = new LogWatchProperties.L0();
        l0.setWindowSeconds(60);
        props.setL0(l0);
        LogWatchProperties.L3 l3 = new LogWatchProperties.L3();
        l3.setRatePerMinute(5);
        l3.setEscalateOccurrenceThreshold(7);
        props.setL3(l3);
        LogWatchProperties.L4 l4 = new LogWatchProperties.L4();
        l4.setDailyLimit(3);
        props.setL4(l4);
        LogWatchProperties.Sse sse = new LogWatchProperties.Sse();
        sse.setBufferSize(64);
        props.setSse(sse);

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getSources()).containsExactly(source);
        assertThat(props.getSources().get(0).getType()).isEqualTo("file");
        assertThat(props.getSources().get(0).getPath()).isEqualTo("/var/log/app.log");
        assertThat(props.getSources().get(0).getKeywords()).containsExactly("ERROR");
        assertThat(props.getExcludeKeywords()).containsExactly("health");
        assertThat(props.getPollIntervalMs()).isEqualTo(1000);
        assertThat(props.getStateDir()).isEqualTo("/data/state");
        assertThat(props.getQueueCapacity()).isEqualTo(100);
        assertThat(props.getL0().getWindowSeconds()).isEqualTo(60);
        assertThat(props.getL3().getRatePerMinute()).isEqualTo(5);
        assertThat(props.getL3().getEscalateOccurrenceThreshold()).isEqualTo(7);
        assertThat(props.getL4().getDailyLimit()).isEqualTo(3);
        assertThat(props.getSse().getBufferSize()).isEqualTo(64);
    }
}
