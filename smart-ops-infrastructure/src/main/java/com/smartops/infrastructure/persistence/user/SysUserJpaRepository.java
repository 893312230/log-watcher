package com.smartops.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 系统用户 Spring Data 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface SysUserJpaRepository extends JpaRepository<SysUserEntity, Long> {

    /**
     * 按登录名查询用户。
     *
     * @param username 登录名
     * @return 用户（不存在时为空）
     */
    Optional<SysUserEntity> findByUsername(String username);
}
