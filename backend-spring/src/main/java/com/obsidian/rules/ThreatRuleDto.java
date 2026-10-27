package com.obsidian.rules;

import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
import com.obsidian.model.RuleSource;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.ThreatRule;
import java.time.Instant;
import java.util.UUID;

/** ThreatRule in web-dashboard/src/lib/types.ts. */
public record ThreatRuleDto(
        UUID id,
        UUID orgId,
        UUID userId,
        IndicatorType indicatorType,
        String indicatorValue,
        ListType listType,
        ThreatCategory threatCategory,
        RuleSource source,
        int confidenceScore,
        Instant expiresAt,
        Instant updatedAt) {

    public static ThreatRuleDto of(ThreatRule rule) {
        return new ThreatRuleDto(rule.getId(), rule.getOrgId(), rule.getUserId(), rule.getIndicatorType(),
                rule.getIndicatorValue(), rule.getListType(), rule.getThreatCategory(), rule.getSource(),
                rule.getConfidenceScore() == null ? 100 : rule.getConfidenceScore(), rule.getExpiresAt(),
                rule.getUpdatedAt());
    }
}
