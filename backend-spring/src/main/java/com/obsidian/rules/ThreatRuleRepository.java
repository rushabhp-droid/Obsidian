package com.obsidian.rules;

import com.obsidian.model.IndicatorType;
import com.obsidian.model.ThreatRule;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Reads only. RuleService writes threat_rules with native upserts. */
public interface ThreatRuleRepository extends Repository<ThreatRule, UUID> {

    /** Every rule that could apply to this user for one indicator; RulePrecedence picks the winner. */
    @Query("""
            select r from ThreatRule r
            where r.indicatorType = :type and r.indicatorValue = :value
              and (r.orgId is null or r.orgId = :orgId)
              and (r.userId is null or r.userId = :userId)""")
    List<ThreatRule> candidates(IndicatorType type, String value, UUID orgId, UUID userId);

    @Query("""
            select r from ThreatRule r
            where (r.expiresAt is null or r.expiresAt > :now)
              and ((r.orgId is null and r.userId is null)
                   or r.userId = :userId
                   or (r.orgId = :orgId and (r.userId is null or :admin = true)))
            order by r.updatedAt desc""")
    List<ThreatRule> visible(UUID orgId, UUID userId, boolean admin, Instant now, Limit limit);

    /** Org-level and global rules of the given types: what daemons enforce. */
    @Query("""
            select r from ThreatRule r
            where r.userId is null and (r.orgId is null or r.orgId = :orgId)
              and r.indicatorType in :types
              and (r.expiresAt is null or r.expiresAt > :now)""")
    List<ThreatRule> organizationRules(UUID orgId, Collection<IndicatorType> types, Instant now);
}
