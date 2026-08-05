package com.smartops.infrastructure.persistence.topology;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopologyNodeJpaRepository extends JpaRepository<TopologyNodeEntity, Long> {}
