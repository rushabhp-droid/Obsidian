package com.obsidian.firewall;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
import com.obsidian.model.ThreatRule;
import java.time.Instant;
import java.util.UUID;

/** BLOCK / BYPASS frame. Daemons get it without {@code at}; dashboards with it. */
public record RuleEvent(
        String type,
        UUID ruleId,
        @JsonProperty("indicator_type") IndicatorType indicatorType,
        String value,
        Instant at) {

    public static RuleEvent of(ThreatRule rule, Instant at) {
        String type = rule.getListType() == ListType.BLACKLIST ? "BLOCK" : "BYPASS";
        return new RuleEvent(type, rule.getId(), rule.getIndicatorType(), rule.getIndicatorValue(), at);
    }
}
