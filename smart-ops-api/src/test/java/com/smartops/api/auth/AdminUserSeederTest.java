package com.smartops.api.auth;

import com.smartops.infrastructure.persistence.user.SysUserEntity;
import com.smartops.infrastructure.persistence.user.SysUserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminUserSeeder} 单元测试。
 *
 * @author smartops
 * @since 1.0.0
 */
class AdminUserSeederTest {

    private final SysUserJpaRepository userRepository = mock(SysUserJpaRepository.class);

    @Test
    @DisplayName("用户表为空时写入 BCrypt 加密的 admin 账号")
    void should_seedAdmin_when_tableEmpty() {
        when(userRepository.count()).thenReturn(0L);
        new AdminUserSeeder(userRepository, "s3cret-pw").run(mock(ApplicationArguments.class));

        ArgumentCaptor<SysUserEntity> captor = ArgumentCaptor.forClass(SysUserEntity.class);
        verify(userRepository).save(captor.capture());
        SysUserEntity saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getRole()).isEqualTo("ADMIN");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(new BCryptPasswordEncoder().matches("s3cret-pw", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("用户表非空时不动作（幂等）")
    void should_skip_when_tableNotEmpty() {
        when(userRepository.count()).thenReturn(2L);
        new AdminUserSeeder(userRepository, "x").run(mock(ApplicationArguments.class));

        verify(userRepository, never()).save(any());
    }
}
