package com.smartops.infrastructure;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试专用 Spring Boot 引导类。
 *
 * <p>infrastructure 模块无生产 @SpringBootApplication（由 bootstrap 模块启动），
 * 切片测试（@DataJpaTest 等）沿包层级向上搜索 @SpringBootConfiguration 时使用本类。
 * 仅存在于测试源码集，不进入产物 jar。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@SpringBootApplication
public class TestApplication {
}
