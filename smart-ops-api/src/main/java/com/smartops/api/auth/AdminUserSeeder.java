package com.smartops.api.auth;

import com.smartops.infrastructure.persistence.user.SysUserEntity;
import com.smartops.infrastructure.persistence.user.SysUserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 初始管理员种子（阶段十二用户体系）。
 *
 * <p>启动时若 sys_user 表为空，写入 admin 账号（角色 ADMIN，
 * 密码取 {@code smartops.auth.admin-initial-password}，默认 admin123，
 * 生产环境必须经环境变量覆盖并首次登录后修改）。表非空时不动作，
 * 保证幂等。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final SysUserJpaRepository userRepository;
    private final String initialPassword;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 构造种子器。
     *
     * @param userRepository  用户仓库
     * @param initialPassword admin 初始密码（仅表为空时生效）
     */
    public AdminUserSeeder(SysUserJpaRepository userRepository,
                           @Value("${smartops.auth.admin-initial-password:admin123}") String initialPassword) {
        this.userRepository = userRepository;
        this.initialPassword = initialPassword;
    }

    /**
     * 启动后执行种子逻辑。
     *
     * @param args 应用参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        SysUserEntity admin = new SysUserEntity();
        admin.setUsername("admin");
        admin.setPassword(encoder.encode(initialPassword));
        admin.setRole("ADMIN");
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);
        log.info("已创建初始管理员账号 admin（请尽快修改初始密码）");
    }
}
