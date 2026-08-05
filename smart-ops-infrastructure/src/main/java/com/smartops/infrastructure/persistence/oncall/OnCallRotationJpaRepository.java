package com.smartops.infrastructure.persistence.oncall;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 值班轮换 Spring Data JPA 仓库。
 *
 * @author smartops
 * @since 1.0.0
 */
public interface OnCallRotationJpaRepository extends JpaRepository<OnCallRotationEntity, Long> {

    /**
     * 按名称查询轮换。
     *
     * @param name 轮换名称
     * @return 轮换实体或空
     */
    Optional<OnCallRotationEntity> findByName(String name);
}
