package com.smartops.infrastructure.persistence.oncall;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 值班轮换 JPA 实体（表 oncall_rotation）。
 *
 * <p>members_json 存放周排班表：{"MON":"张三",...,"SUN":"李四"}。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "oncall_rotation")
public class OnCallRotationEntity {

    /** 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 轮换名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 周排班表（JSON：星期缩写 → 值班人）。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String membersJson;

    /** 交接日（预留）。 */
    @Column(nullable = false, length = 16)
    private String handoffDay;

    /** 当前成员索引（预留）。 */
    @Column(nullable = false)
    private int currentIndex;
}
