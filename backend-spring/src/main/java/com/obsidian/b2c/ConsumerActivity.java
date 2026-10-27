package com.obsidian.b2c;

import com.obsidian.model.ThreatCategory;
import com.obsidian.model.Verdict;
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

/** consumer_activity row: one entry in the app's Activity tab. Never holds message text. */
@Entity
@Table(name = "consumer_activity")
public class ConsumerActivity {

    public enum Kind { URL_SCAN, FILE_SCAN, LINK_OPENED, MESSAGE, REPORT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "consumer_id", nullable = false)
    private UUID consumerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false)
    private String subject;

    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Verdict verdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_category", length = 30)
    private ThreatCategory threatCategory;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "vt_detections", nullable = false)
    private int vtDetections;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected ConsumerActivity() {}

    public ConsumerActivity(UUID consumerId, Kind kind, String subject, String detail, Verdict verdict,
            ThreatCategory threatCategory, Integer riskScore, int vtDetections) {
        this.consumerId = consumerId;
        this.kind = kind;
        this.subject = subject;
        this.detail = detail;
        this.verdict = verdict;
        this.threatCategory = threatCategory;
        this.riskScore = riskScore;
        this.vtDetections = vtDetections;
    }

    public UUID getId() { return id; }
    public UUID getConsumerId() { return consumerId; }
    public Kind getKind() { return kind; }
    public String getSubject() { return subject; }
    public String getDetail() { return detail; }
    public Verdict getVerdict() { return verdict; }
    public ThreatCategory getThreatCategory() { return threatCategory; }
    public Integer getRiskScore() { return riskScore; }
    public int getVtDetections() { return vtDetections; }
    public Instant getCreatedAt() { return createdAt; }
}
