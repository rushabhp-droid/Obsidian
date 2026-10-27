package com.obsidian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * threat_rules row, read-only through JPA: RuleService is the only writer and
 * upserts with native SQL (insert, audit, publish).
 */
@Entity
@Immutable
@Table(name = "threat_rules")
public class ThreatRule {

    @Id
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", nullable = false, length = 20)
    private IndicatorType indicatorType;

    @Column(name = "indicator_value", nullable = false)
    private String indicatorValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_type", nullable = false, length = 12)
    private ListType listType;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_category", nullable = false, length = 30)
    private ThreatCategory threatCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuleSource source;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ThreatRule() {}

    /** For building rules in memory (tests, precedence); never persisted through JPA. */
    public ThreatRule(UUID id, UUID orgId, UUID userId, IndicatorType indicatorType, String indicatorValue,
            ListType listType, ThreatCategory threatCategory, RuleSource source, Integer confidenceScore,
            Instant expiresAt, Instant updatedAt) {
        this.id = id;
        this.orgId = orgId;
        this.userId = userId;
        this.indicatorType = indicatorType;
        this.indicatorValue = indicatorValue;
        this.listType = listType;
        this.threatCategory = threatCategory;
        this.source = source;
        this.confidenceScore = confidenceScore;
        this.expiresAt = expiresAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public UUID getUserId() { return userId; }
    public IndicatorType getIndicatorType() { return indicatorType; }
    public String getIndicatorValue() { return indicatorValue; }
    public ListType getListType() { return listType; }
    public ThreatCategory getThreatCategory() { return threatCategory; }
    public RuleSource getSource() { return source; }
    public Integer getConfidenceScore() { return confidenceScore; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean isGlobal() {
        return orgId == null && userId == null;
    }
}
