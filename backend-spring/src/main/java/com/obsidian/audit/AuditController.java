package com.obsidian.audit;

import com.obsidian.config.ForbiddenException;
import com.obsidian.model.Account;
import com.obsidian.model.AuditAction;
import com.obsidian.model.AuditLog;
import com.obsidian.model.AuditLogRepository;
import com.obsidian.model.User;
import com.obsidian.model.UserRepository;
import com.obsidian.rules.ThreatRuleDto;
import com.obsidian.rules.ThreatRuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard's audit log: read-only, newest first. Admins see their
 * organization's records and the global blocks promoted from consumer reports.
 * Records are never edited or deleted through the API.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private static final int MAX_LIMIT = 500;

    private final AuditLogRepository logs;
    private final UserRepository users;
    private final ThreatRuleRepository rules;

    public AuditController(AuditLogRepository logs, UserRepository users, ThreatRuleRepository rules) {
        this.logs = logs;
        this.users = users;
        this.rules = rules;
    }

    public record Actor(UUID id, String email, String displayName) {}

    /** {@code actor} is null for automatic changes (VirusTotal, reporter thresholds) and for removed accounts. */
    public record AuditEntry(UUID id, AuditAction action, Actor actor, UUID orgId, UUID ruleId,
            Map<String, Object> details, Instant createdAt) {}

    /** One record, the rule it changed (as it is now), and that rule's whole history. */
    public record AuditDetail(AuditEntry entry, ThreatRuleDto rule, List<AuditEntry> ruleHistory) {}

    @GetMapping
    public List<AuditEntry> list(Account account, @RequestParam(defaultValue = "200") int limit) {
        requireAdmin(account);
        List<AuditLog> rows = logs.visibleTo(account.orgId(), Limit.of(Math.max(1, Math.min(limit, MAX_LIMIT))));
        return entries(rows);
    }

    @GetMapping("/{id}")
    public AuditDetail detail(Account account, @PathVariable UUID id) {
        requireAdmin(account);
        AuditLog log = logs.findById(id).filter(l -> visible(l, account)).orElseThrow(AuditRecordNotFound::new);
        ThreatRuleDto rule = log.getRuleId() == null ? null : rules.findById(log.getRuleId()).map(ThreatRuleDto::of).orElse(null);
        List<AuditEntry> history = log.getRuleId() == null ? List.of()
                : entries(logs.findByRuleIdOrderByCreatedAtAsc(log.getRuleId()).stream().filter(l -> visible(l, account)).toList());
        return new AuditDetail(entries(List.of(log)).getFirst(), rule, history);
    }

    private List<AuditEntry> entries(List<AuditLog> rows) {
        List<UUID> actorIds = rows.stream().map(AuditLog::getActorUserId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, User> actors = users.findAllById(actorIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().map(l -> new AuditEntry(l.getId(), l.getAction(), actor(actors.get(l.getActorUserId())),
                l.getOrgId(), l.getRuleId(), l.getDetails() == null ? Map.of() : l.getDetails(), l.getCreatedAt())).toList();
    }

    private static Actor actor(User user) {
        if (user == null) {
            return null;
        }
        String name = user.getDisplayName() != null && !user.getDisplayName().isBlank() ? user.getDisplayName()
                : user.getEmail().substring(0, user.getEmail().indexOf('@'));
        return new Actor(user.getId(), user.getEmail(), name);
    }

    private static boolean visible(AuditLog log, Account account) {
        return account.orgId().equals(log.getOrgId())
                || (log.getOrgId() == null && log.getAction() == AuditAction.CONSUMER_REPORT);
    }

    private static void requireAdmin(Account account) {
        if (!account.isAdmin()) {
            throw new ForbiddenException("Only organization admins can read the audit log.");
        }
    }

    static class AuditRecordNotFound extends RuntimeException {
        AuditRecordNotFound() {
            super("No such audit record.");
        }
    }

    @ExceptionHandler(AuditRecordNotFound.class)
    ProblemDetail notFound(AuditRecordNotFound e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
