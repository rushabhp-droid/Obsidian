package com.obsidian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Column(name = "rule_id")
    private UUID ruleId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected AuditLog() {}

    public AuditLog(AuditAction action, UUID orgId, UUID actorUserId, UUID ruleId, Map<String, Object> details) {
        this.action = action;
        this.orgId = orgId;
        this.actorUserId = actorUserId;
        this.ruleId = ruleId;
        this.details = details;
    }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public UUID getActorUserId() { return actorUserId; }
    public AuditAction getAction() { return action; }
    public UUID getRuleId() { return ruleId; }
    public Map<String, Object> getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
