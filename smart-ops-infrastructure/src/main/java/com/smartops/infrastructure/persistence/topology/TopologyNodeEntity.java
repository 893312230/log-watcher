package com.smartops.infrastructure.persistence.topology;

import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;

@Getter @Setter
@Entity
@Table(name = "topology_node", indexes = {@Index(name="idx_tn_name", columnList="name")})
public class TopologyNodeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 128) private String name;
    @Column(length = 32) private String type;
    @Column(length = 256) private String host;
    @Column(length = 16) private String status;
    @Column(columnDefinition = "TEXT") private String metadata;
}
