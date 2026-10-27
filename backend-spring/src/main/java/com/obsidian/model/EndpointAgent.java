package com.obsidian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A registered Rust daemon. Only the SHA-256 of its bearer token is stored. */
@Entity
@Table(name = "endpoint_agents")
public class EndpointAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String hostname;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected EndpointAgent() {}

    public EndpointAgent(UUID orgId, String hostname, String tokenHash) {
        this.orgId = orgId;
        this.hostname = hostname;
        this.tokenHash = tokenHash;
    }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public String getHostname() { return hostname; }
    public String getTokenHash() { return tokenHash; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public Instant getCreatedAt() { return createdAt; }
}
