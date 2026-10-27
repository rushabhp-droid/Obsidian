package com.obsidian.b2c;

import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
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

/** consumer_rules row: one entry of a consumer's personal blacklist or whitelist. */
@Entity
@Table(name = "consumer_rules")
public class ConsumerRule {

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
    @Column(name = "list_type", nullable = false, length = 12)
    private ListType listType;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected ConsumerRule() {}

    public ConsumerRule(UUID consumerId, IndicatorType indicatorType, String indicatorValue, ListType listType) {
        this.consumerId = consumerId;
        this.indicatorType = indicatorType;
        this.indicatorValue = indicatorValue;
        this.listType = listType;
    }

    public UUID getId() { return id; }
    public UUID getConsumerId() { return consumerId; }
    public IndicatorType getIndicatorType() { return indicatorType; }
    public String getIndicatorValue() { return indicatorValue; }
    public ListType getListType() { return listType; }
    public void setListType(ListType listType) { this.listType = listType; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
