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
import java.util.UUID;

/** One row per scan request; indicator_checked is its worst indicator. */
@Entity
@Table(name = "scan_logs")
public class ScanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    private String sender;

    @Column(name = "indicator_checked")
    private String indicatorChecked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Verdict verdict;

    @Column(name = "vt_positives")
    private int vtPositives;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected ScanLog() {}

    public ScanLog(UUID userId, Channel channel, String sender, String indicatorChecked, Verdict verdict,
            int vtPositives) {
        this.userId = userId;
        this.channel = channel;
        this.sender = sender;
        this.indicatorChecked = indicatorChecked;
        this.verdict = verdict;
        this.vtPositives = vtPositives;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Channel getChannel() { return channel; }
    public String getSender() { return sender; }
    public String getIndicatorChecked() { return indicatorChecked; }
    public Verdict getVerdict() { return verdict; }
    public int getVtPositives() { return vtPositives; }
    public Instant getCreatedAt() { return createdAt; }
}
