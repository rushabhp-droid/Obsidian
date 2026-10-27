package com.obsidian.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface EndpointAgentRepository extends JpaRepository<EndpointAgent, UUID> {

    Optional<EndpointAgent> findByTokenHash(String tokenHash);

    List<EndpointAgent> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    @Transactional
    @Modifying
    @Query("update EndpointAgent a set a.lastHeartbeatAt = :at where a.id = :id")
    int touchHeartbeat(UUID id, Instant at);
}
