package com.smartops.infrastructure.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 系统用户 JPA 实体（表 sys_user）。
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class SysUserEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名（唯一）。 */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** 密码（BCrypt 散列）。 */
    @Column(nullable = false, length = 128)
    private String password;

    /** 角色（ADMIN / VIEWER）。 */
    @Column(nullable = false, length = 32)
    private String role;

    /** 创建时间。 */
    @Column(nullable = false)
    private Instant createdAt;
}
