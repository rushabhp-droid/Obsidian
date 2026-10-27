package com.obsidian.b2c;

import com.obsidian.b2c.ConsumerReport.Status;
import com.obsidian.config.ForbiddenException;
import com.obsidian.model.Account;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ThreatCategory;
import com.obsidian.rules.Indicators;
import com.obsidian.rules.ThreatRuleDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard's "Consumer reports" queue (admins only): consumer reports that
 * are still waiting, or that hit a protected domain, grouped by indicator.
 * Approving blocks the indicator globally; the protected-domain list is edited here too.
 */
@RestController
public class ConsumerReportAdminController {

    private static final int MAX_NOTES = 5;

    private final ConsumerReportService service;
    private final ConsumerReportRepository reports;
    private final ProtectedDomains protectedDomains;

    public ConsumerReportAdminController(ConsumerReportService service, ConsumerReportRepository reports,
            ProtectedDomains protectedDomains) {
        this.service = service;
        this.reports = reports;
        this.protectedDomains = protectedDomains;
    }

    public record IndicatorRequest(@NotNull IndicatorType indicatorType, @NotBlank @Size(max = 2048) String indicatorValue) {}

    public record DomainRequest(@NotBlank @Size(max = 253) String domain) {}

    /** One row per reported indicator. {@code protectedDomain}: approval isn't possible. */
    public record QueuedReport(IndicatorType indicatorType, String indicatorValue, ThreatCategory threatCategory,
            Status status, boolean protectedDomain, long reporters, int reportersNeeded, List<String> notes,
            Instant firstReportedAt, Instant lastReportedAt) {}

    @GetMapping("/api/v1/consumer-reports")
    public List<QueuedReport> queue(Account account) {
        requireAdmin(account);
        Map<String, List<ConsumerReport>> byIndicator = new LinkedHashMap<>();
        reports.findByStatusInOrderByCreatedAtDesc(List.of(Status.WAITING, Status.PROTECTED))
                .forEach(r -> byIndicator.computeIfAbsent(r.getIndicatorType() + " " + r.getIndicatorValue(),
                        k -> new ArrayList<>()).add(r));
        return byIndicator.values().stream().map(group -> {
            ConsumerReport latest = group.getFirst();
            boolean domainProtected = latest.getIndicatorType() == IndicatorType.DOMAIN
                    && protectedDomains.isProtected(latest.getIndicatorValue());
            return new QueuedReport(latest.getIndicatorType(), latest.getIndicatorValue(), latest.getThreatCategory(),
                    latest.getStatus(), domainProtected,
                    service.reporters(latest.getIndicatorType(), latest.getIndicatorValue()),
                    service.reportersNeeded(),
                    group.stream().map(ConsumerReport::getNote).filter(Objects::nonNull).limit(MAX_NOTES).toList(),
                    group.stream().map(ConsumerReport::getCreatedAt).min(Comparator.naturalOrder()).orElse(null),
                    latest.getCreatedAt());
        }).toList();
    }

    @PostMapping("/api/v1/consumer-reports/approve")
    public ThreatRuleDto approve(Account account, @Valid @RequestBody IndicatorRequest body) {
        requireAdmin(account);
        return ThreatRuleDto.of(service.approve(account, body.indicatorType(), body.indicatorValue()));
    }

    @PostMapping("/api/v1/consumer-reports/dismiss")
    public ResponseEntity<Void> dismiss(Account account, @Valid @RequestBody IndicatorRequest body) {
        requireAdmin(account);
        service.dismiss(body.indicatorType(), body.indicatorValue());
        return ResponseEntity.noContent().build();
    }

    /** Admin additions only; the bundled top-domain list isn't listed. */
    @GetMapping("/api/v1/protected-domains")
    public List<String> protectedDomains(Account account) {
        requireAdmin(account);
        return protectedDomains.adminAdded();
    }

    @PostMapping("/api/v1/protected-domains")
    public List<String> addProtectedDomain(Account account, @Valid @RequestBody DomainRequest body) {
        requireAdmin(account);
        protectedDomains.add(Indicators.requireValid(IndicatorType.DOMAIN, body.domain()), account.userId());
        return protectedDomains.adminAdded();
    }

    @DeleteMapping("/api/v1/protected-domains/{domain}")
    public ResponseEntity<Void> removeProtectedDomain(Account account, @PathVariable String domain) {
        requireAdmin(account);
        protectedDomains.remove(Indicators.normalize(IndicatorType.DOMAIN, domain));
        return ResponseEntity.noContent().build();
    }

    private static void requireAdmin(Account account) {
        if (!account.isAdmin()) {
            throw new ForbiddenException("Only organization admins can review consumer reports.");
        }
    }
}
