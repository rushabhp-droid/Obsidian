package com.obsidian.b2c;

import com.obsidian.model.IndicatorType;
import com.obsidian.model.ThreatCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** consumer_reports row: one consumer's report of one indicator, and how far it got. */
@Entity
@Table(name = "consumer_reports")
public class ConsumerReport {

    /** WAITING for more reporters or an admin; PROTECTED domain (admin review only); BLOCKED globally. */
    public enum Status { WAITING, PROTECTED, BLOCKED, DISMISSED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "consumer_id", nullable = false)
    private UUID consumerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", nullable = false, length = 20)
    private IndicatorType indicatorType;

    @Column(name = "indicator_value", nullable = false)
    private String indicatorValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_category", nullable = false, length = 30)
    private ThreatCategory threatCategory;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected ConsumerReport() {}

    public ConsumerReport(UUID consumerId, IndicatorType indicatorType, String indicatorValue,
            ThreatCategory threatCategory, String note, Status status) {
        this.consumerId = consumerId;
        this.indicatorType = indicatorType;
        this.indicatorValue = indicatorValue;
        this.threatCategory = threatCategory;
        this.note = note;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getConsumerId() { return consumerId; }
    public IndicatorType getIndicatorType() { return indicatorType; }
    public String getIndicatorValue() { return indicatorValue; }
    public ThreatCategory getThreatCategory() { return threatCategory; }
    public void setThreatCategory(ThreatCategory threatCategory) { this.threatCategory = threatCategory; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
