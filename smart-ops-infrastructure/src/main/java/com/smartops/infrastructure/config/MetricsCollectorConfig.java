package com.smartops.infrastructure.config;

import com.smartops.infrastructure.metrics.PrometheusMetricCollector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "smartops.metrics.prometheus-url")
public class MetricsCollectorConfig {

    @Bean
    public PrometheusMetricCollector prometheusMetricCollector(
            @org.springframework.beans.factory.annotation.Value("${smartops.metrics.prometheus-url}") String url) {
        return new PrometheusMetricCollector(url);
    }
}
