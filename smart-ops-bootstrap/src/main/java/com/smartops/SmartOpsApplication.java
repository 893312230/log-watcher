package com.smartops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 智能运维 Agent 平台启动类。
 *
 * <p>通过 {@code @SpringBootApplication} 注解触发 Spring Boot 自动配置，
 * 扫描范围覆盖 {@code com.smartops} 下所有子包。
 * {@code @EnableAsync} 启用异步方法执行（Webhook 事件异步投递）。</p>
 *
 * <p>启动后可通过 REST API 与 Agent 交互，详见 smart-ops-api 模块的 Controller。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@EnableAsync
@SpringBootApplication(scanBasePackages = "com.smartops")
public class SmartOpsApplication {

    /**
     * 应用入口方法。
     *
     * @param args 启动参数，支持 Spring Boot 标准参数（如 --server.port=8080）
     */
    public static void main(String[] args) {
        SpringApplication.run(SmartOpsApplication.class, args);
    }
}
