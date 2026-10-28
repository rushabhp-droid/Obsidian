package com.obsidian.model;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByRuleIdOrderByCreatedAtAsc(UUID ruleId);

    /** An organization's audit trail, plus global rule changes (promoted consumer reports) that reach its daemons. */
    @Query("""
            select a from AuditLog a
            where a.orgId = :orgId or (a.orgId is null and a.action = com.obsidian.model.AuditAction.CONSUMER_REPORT)
            order by a.createdAt desc""")
    List<AuditLog> visibleTo(UUID orgId, Limit limit);
}
