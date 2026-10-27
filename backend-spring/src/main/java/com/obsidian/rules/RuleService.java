package com.obsidian.rules;

import com.obsidian.audit.AuditService;
import com.obsidian.model.Account;
import com.obsidian.model.AuditAction;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
import com.obsidian.model.RuleSource;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.ThreatRule;
import com.obsidian.rules.RulePrecedence.Scope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of threat_rules. Every mutation upserts one row per
 * (scope, indicator), writes audit_logs, and publishes the change on Redis
 * (after commit, through {@link RuleChanged}).
 *
 * <p>Scope by role: admins write org rules as ADMIN_POLICY; an employee's
 * report is an org rule (it blocks for colleagues) while their bypass is
 * personal and must expire; consumers only have personal rules.
 */
@Service
public class RuleService {

    static final int MAX_TTL_MINUTES = 1440;
    private static final int VISIBLE_LIMIT = 1000;
    private static final List<IndicatorType> NETWORK_TYPES = List.of(IndicatorType.DOMAIN, IndicatorType.IP);

    private static final String COLUMNS = "id, org_id, user_id, indicator_type, indicator_value, list_type,"
            + " threat_category, source, confidence_score, expires_at, updated_at";

    private static final String LOCK_SQL = "select " + COLUMNS + " from threat_rules"
            + " where org_id is not distinct from cast(? as uuid) and user_id is not distinct from cast(? as uuid)"
            + " and indicator_type = ? and indicator_value = ? for update";

    private static final String UPSERT_SQL = """
            insert into threat_rules (org_id, user_id, indicator_type, indicator_value, list_type,
                                      threat_category, source, confidence_score, expires_at, updated_at)
            values (cast(? as uuid), cast(? as uuid), ?, ?, ?, ?, ?, 100, ?, now())
            on conflict (org_id, user_id, indicator_type, indicator_value) do update
               set list_type = excluded.list_type, threat_category = excluded.threat_category,
                   source = excluded.source, confidence_score = excluded.confidence_score,
                   expires_at = excluded.expires_at, updated_at = now()
            returning\s""" + COLUMNS;

    private static final RowMapper<ThreatRule> ROW = RuleService::mapRow;

    private final JdbcTemplate jdbc;
    private final ThreatRuleRepository repository;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RuleService(JdbcTemplate jdbc, ThreatRuleRepository repository, AuditService audit,
            ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    private record Target(UUID orgId, UUID userId, RuleSource source) {}

    @Transactional
    public ThreatRule report(Account actor, IndicatorType type, String rawValue, ThreatCategory category) {
        String value = Indicators.requireValid(type, rawValue);
        ThreatCategory threatCategory = category == null ? ThreatCategory.MALICIOUS : category;
        if (threatCategory == ThreatCategory.SAFE) {
            throw new InvalidRuleRequestException("A report needs a threat category other than SAFE.");
        }
        Target target = actor.isAdmin()
                ? new Target(actor.orgId(), null, RuleSource.ADMIN_POLICY)
                : actor.inOrganization()
                        ? new Target(actor.orgId(), null, RuleSource.USER_REPORT)
                        : new Target(null, actor.userId(), RuleSource.USER_REPORT);
        return write(actor, AuditAction.REPORT, target, type, value, ListType.BLACKLIST, threatCategory, null, null);
    }

    @Transactional
    public ThreatRule bypass(Account actor, IndicatorType type, String rawValue, Integer ttlMinutes) {
        String value = Indicators.requireValid(type, rawValue);
        if (ttlMinutes != null && (ttlMinutes < 1 || ttlMinutes > MAX_TTL_MINUTES)) {
            throw new InvalidRuleRequestException("ttlMinutes must be between 1 and " + MAX_TTL_MINUTES + ".");
        }
        Target target;
        if (actor.isAdmin()) {
            target = new Target(actor.orgId(), null, RuleSource.ADMIN_POLICY);
        } else if (actor.inOrganization()) {
            if (!actor.allowUserBypass()) {
                throw new BypassNotAllowedException();
            }
            if (ttlMinutes == null) {
                throw new InvalidRuleRequestException("Bypasses in an organization need ttlMinutes.");
            }
            if (adminPolicy(actor.orgId(), type, value).filter(r -> r.getListType() == ListType.BLACKLIST).isPresent()) {
                throw new RuleConflictException("An administrator policy blocks this indicator. Ask an administrator.");
            }
            target = new Target(actor.orgId(), actor.userId(), RuleSource.USER_BYPASS);
        } else {
            target = new Target(null, actor.userId(), RuleSource.USER_BYPASS);
        }
        Instant expiresAt = ttlMinutes == null ? null : clock.instant().plus(ttlMinutes, ChronoUnit.MINUTES);
        return write(actor, AuditAction.BYPASS, target, type, value, ListType.WHITELIST, ThreatCategory.SAFE, expiresAt,
                null);
    }

    /**
     * A B2C consumer report promoted to a global block (no org, no user): every
     * organization's daemons and every app user get it. {@code approver} is the
     * admin who approved it, or null when VirusTotal or enough reporters did.
     * {@code reason} goes into the audit row (consumer ids, never their emails).
     */
    @Transactional
    public ThreatRule blockGlobally(Account approver, IndicatorType type, String rawValue, ThreatCategory category,
            RuleSource source, Map<String, Object> reason) {
        String value = Indicators.requireValid(type, rawValue);
        return write(approver, AuditAction.CONSUMER_REPORT, new Target(null, null, source), type, value,
                ListType.BLACKLIST, category == null ? ThreatCategory.MALICIOUS : category, null, reason);
    }

    /** The rule that decides this indicator for this user, after precedence. Value must be normalized. */
    @Transactional(readOnly = true)
    public Optional<ThreatRule> decide(Account account, IndicatorType type, String value) {
        List<ThreatRule> candidates = repository.candidates(type, value, account.orgId(), account.userId());
        return RulePrecedence.resolve(candidates, scopeOf(account), clock.instant());
    }

    /** GET /api/v1/rules: unexpired global, org and own rules; admins also see employees' rules. */
    @Transactional(readOnly = true)
    public List<ThreatRule> visibleRules(Account account) {
        return repository.visible(account.orgId(), account.userId(), account.isAdmin(), clock.instant(),
                Limit.of(VISIBLE_LIMIT));
    }

    /** What the organization's daemons must block right now: org + global IP/DOMAIN rules after precedence. */
    @Transactional(readOnly = true)
    public List<FirewallRule> firewallBlocks(UUID orgId) {
        Instant now = clock.instant();
        Scope scope = Scope.organization(orgId, true);
        Map<String, List<ThreatRule>> byIndicator = repository.organizationRules(orgId, NETWORK_TYPES, now).stream()
                .collect(Collectors.groupingBy(r -> r.getIndicatorType() + " " + r.getIndicatorValue(),
                        LinkedHashMap::new, Collectors.toList()));
        return byIndicator.values().stream()
                .map(candidates -> RulePrecedence.resolve(candidates, scope, now))
                .flatMap(Optional::stream)
                .filter(rule -> rule.getListType() == ListType.BLACKLIST)
                .sorted(Comparator.comparing(ThreatRule::getIndicatorValue))
                .map(rule -> new FirewallRule(rule.getId(), rule.getIndicatorType(), rule.getIndicatorValue()))
                .toList();
    }

    private ThreatRule write(Account actor, AuditAction action, Target target, IndicatorType type, String value,
            ListType list, ThreatCategory category, Instant expiresAt, Map<String, Object> extra) {
        Optional<ThreatRule> previous = jdbc.query(LOCK_SQL, ROW, target.orgId(), target.userId(), type.name(), value)
                .stream().findFirst();
        if (previous.isPresent() && previous.get().getSource() == RuleSource.ADMIN_POLICY
                && target.source() != RuleSource.ADMIN_POLICY) {
            throw new RuleConflictException("An administrator policy already covers this indicator.");
        }

        ThreatRule rule = jdbc.queryForObject(UPSERT_SQL, ROW, target.orgId(), target.userId(), type.name(), value,
                list.name(), category.name(), target.source().name(),
                expiresAt == null ? null : OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("indicatorType", type.name());
        details.put("indicatorValue", value);
        details.put("listType", list.name());
        details.put("previousListType", previous.map(r -> r.getListType().name()).orElse(null));
        details.put("threatCategory", category.name());
        details.put("source", target.source().name());
        details.put("scope", target.userId() != null ? "USER" : target.orgId() != null ? "ORGANIZATION" : "GLOBAL");
        details.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        if (extra != null) {
            details.putAll(extra);
        }
        audit.record(action, actor == null ? null : actor.orgId(), actor == null ? null : actor.userId(), rule.getId(),
                details);

        events.publishEvent(new RuleChanged(rule, clock.instant()));
        return rule;
    }

    private Optional<ThreatRule> adminPolicy(UUID orgId, IndicatorType type, String value) {
        Instant now = clock.instant();
        return repository.candidates(type, value, orgId, null).stream()
                .filter(r -> orgId.equals(r.getOrgId()) && r.getUserId() == null)
                .filter(r -> r.getSource() == RuleSource.ADMIN_POLICY && !r.isExpired(now))
                .findFirst();
    }

    private static Scope scopeOf(Account account) {
        return new Scope(account.orgId(), account.userId(), account.allowUserBypass());
    }

    private static ThreatRule mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime expires = rs.getObject("expires_at", OffsetDateTime.class);
        OffsetDateTime updated = rs.getObject("updated_at", OffsetDateTime.class);
        return new ThreatRule(
                rs.getObject("id", UUID.class),
                rs.getObject("org_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                IndicatorType.valueOf(rs.getString("indicator_type")),
                rs.getString("indicator_value"),
                ListType.valueOf(rs.getString("list_type")),
                ThreatCategory.valueOf(rs.getString("threat_category")),
                RuleSource.valueOf(rs.getString("source")),
                (Integer) rs.getObject("confidence_score"),
                expires == null ? null : expires.toInstant(),
                updated == null ? null : updated.toInstant());
    }
}
