package com.smartops.infrastructure.persistence.topology;

import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;

@Getter @Setter
@Entity
@Table(name = "topology_edge", indexes = {@Index(name="idx_te_source", columnList="sourceId"), @Index(name="idx_te_target", columnList="targetId")})
public class TopologyEdgeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long sourceId;
    @Column(nullable = false) private Long targetId;
    @Column(length = 32) private String type;
}
