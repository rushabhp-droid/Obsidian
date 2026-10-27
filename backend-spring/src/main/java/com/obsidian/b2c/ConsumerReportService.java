package com.obsidian.b2c;

import com.obsidian.b2c.B2cDtos.Report;
import com.obsidian.b2c.ConsumerReport.Status;
import com.obsidian.model.Account;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
import com.obsidian.model.RuleSource;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.ThreatRule;
import com.obsidian.model.Verdict;
import com.obsidian.rules.Indicators;
import com.obsidian.rules.InvalidRuleRequestException;
import com.obsidian.rules.RuleConflictException;
import com.obsidian.rules.RuleService;
import com.obsidian.rules.ThreatRuleRepository;
import com.obsidian.scanner.VirusTotalClient;
import com.obsidian.scanner.VtVerdict;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer reports and when they become a global block (threat_rules, through
 * RuleService, so B2B daemons enforce them too). The reporter is protected at
 * once by a personal blacklist rule. Globally, a report blocks right away only
 * when VirusTotal says MALICIOUS; otherwise it waits for
 * obsidian.b2c.reporters-to-block verified consumers within the report window,
 * or an admin's approval. A protected domain is never blocked globally from a
 * consumer report, and a URL on a protected domain needs an admin.
 */
@Service
public class ConsumerReportService {

    private static final Logger log = LoggerFactory.getLogger(ConsumerReportService.class);
    private static final int RECHECK_BATCH = 20;

    private final ConsumerReportRepository reports;
    private final ConsumerRuleRepository consumerRules;
    private final ConsumerActivityRepository activity;
    private final ThreatRuleRepository threatRules;
    private final RuleService rules;
    private final VirusTotalClient virusTotal;
    private final ProtectedDomains protectedDomains;
    private final B2cProperties properties;
    private final Clock clock;

    public ConsumerReportService(ConsumerReportRepository reports, ConsumerRuleRepository consumerRules,
            ConsumerActivityRepository activity, ThreatRuleRepository threatRules, RuleService rules,
            VirusTotalClient virusTotal, ProtectedDomains protectedDomains, B2cProperties properties, Clock clock) {
        this.reports = reports;
        this.consumerRules = consumerRules;
        this.activity = activity;
        this.threatRules = threatRules;
        this.rules = rules;
        this.virusTotal = virusTotal;
        this.protectedDomains = protectedDomains;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Report report(Consumer consumer, IndicatorType type, String rawValue, ThreatCategory rawCategory, String note) {
        String value = Indicators.requireValid(type, rawValue);
        ThreatCategory category = rawCategory == null ? ThreatCategory.MALICIOUS : rawCategory;
        if (category == ThreatCategory.SAFE) {
            throw new InvalidRuleRequestException("A report needs a threat category other than SAFE.");
        }

        ConsumerRule personal = consumerRules
                .findByConsumerIdAndIndicatorTypeAndIndicatorValue(consumer.getId(), type, value)
                .orElseGet(() -> new ConsumerRule(consumer.getId(), type, value, ListType.BLACKLIST));
        personal.setListType(ListType.BLACKLIST);
        consumerRules.save(personal);

        ConsumerReport report = reports.findByConsumerIdAndIndicatorTypeAndIndicatorValue(consumer.getId(), type, value)
                .orElseGet(() -> new ConsumerReport(consumer.getId(), type, value, category, null, Status.WAITING));
        report.setThreatCategory(category);
        report.setNote(note == null || note.isBlank() ? report.getNote() : note.trim());
        if (report.getStatus() == Status.DISMISSED) {
            report.setStatus(Status.WAITING);
            report.setCreatedAt(clock.instant()); // a new report after a dismissal counts afresh
        }
        report = reports.saveAndFlush(report);

        activity.save(new ConsumerActivity(consumer.getId(), ConsumerActivity.Kind.REPORT, value, type.name(),
                Verdict.MALICIOUS, category, 95, 0));
        evaluate(type, value, category);
        return dto(reports.findById(report.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<Report> mine(Consumer consumer) {
        return reports.findByConsumerIdOrderByCreatedAtDesc(consumer.getId()).stream().map(this::dto).toList();
    }

    /** Admin approval from the dashboard. A protected domain can't be approved: block it in the org's rules instead. */
    @Transactional
    public ThreatRule approve(Account admin, IndicatorType type, String rawValue) {
        String value = Indicators.requireValid(type, rawValue);
        List<ConsumerReport> open = openReports(type, value);
        if (open.isEmpty()) {
            throw B2cException.notFound("No open consumer report for this indicator.");
        }
        if (type == IndicatorType.DOMAIN && protectedDomains.isProtected(value)) {
            throw new RuleConflictException(
                    "This is a protected domain, so it can't be blocked for everyone. Block it in your organization's rules instead.");
        }
        Map<String, Object> reason = reason("ADMIN_APPROVAL", open);
        return promote(admin, type, value, open.getFirst().getThreatCategory(), RuleSource.USER_REPORT, reason, open);
    }

    @Transactional
    public void dismiss(IndicatorType type, String rawValue) {
        String value = Indicators.requireValid(type, rawValue);
        List<ConsumerReport> open = openReports(type, value);
        if (open.isEmpty()) {
            throw B2cException.notFound("No open consumer report for this indicator.");
        }
        open.forEach(r -> r.setStatus(Status.DISMISSED));
        reports.saveAll(open);
    }

    /** Reports that VirusTotal hadn't finished with may be decided by now. */
    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT10M")
    public void recheckWaiting() {
        Instant since = clock.instant().minus(properties.reportWindow());
        reports.waitingSince(since).stream().limit(RECHECK_BATCH).forEach(key -> {
            try {
                List<ConsumerReport> open = openReports(key.getType(), key.getValue());
                if (!open.isEmpty()) {
                    evaluate(key.getType(), key.getValue(), open.getFirst().getThreatCategory());
                }
            } catch (RuntimeException e) {
                log.warn("Rechecking a consumer report failed: {}", e.getMessage());
            }
        });
    }

    /** Decides the shared outcome for every open report of this indicator. */
    void evaluate(IndicatorType type, String value, ThreatCategory category) {
        List<ConsumerReport> open = openReports(type, value);
        Optional<ThreatRule> existing = globalBlock(type, value);
        if (existing.isPresent()) {
            mark(open, Status.BLOCKED, existing.get());
            return;
        }
        if (isProtected(type, value)) {
            open.forEach(r -> r.setStatus(Status.PROTECTED));
            reports.saveAll(open);
            return;
        }
        VtVerdict vt = lookup(type, value);
        if (vt.verdict() == Verdict.MALICIOUS) {
            Map<String, Object> reason = reason("VIRUSTOTAL", open);
            reason.put("vtDetections", vt.malicious());
            promote(null, type, value, vt.category() != null && category == ThreatCategory.MALICIOUS ? vt.category()
                    : category, RuleSource.VIRUSTOTAL, reason, open);
            return;
        }
        long reporters = reporters(type, value);
        if (reporters >= properties.reportersToBlock()) {
            Map<String, Object> reason = reason("REPORTERS", open);
            reason.put("reporters", reporters);
            promote(null, type, value, category, RuleSource.USER_REPORT, reason, open);
        }
    }

    private ThreatRule promote(Account approver, IndicatorType type, String value, ThreatCategory category,
            RuleSource source, Map<String, Object> reason, List<ConsumerReport> open) {
        ThreatRule rule = rules.blockGlobally(approver, type, value, category, source, reason);
        mark(open, Status.BLOCKED, rule);
        return rule;
    }

    private void mark(List<ConsumerReport> open, Status status, ThreatRule rule) {
        open.forEach(r -> {
            r.setStatus(status);
            r.setRuleId(rule.getId());
        });
        reports.saveAll(open);
    }

    private boolean isProtected(IndicatorType type, String value) {
        if (type == IndicatorType.DOMAIN) {
            return protectedDomains.isProtected(value);
        }
        if (type == IndicatorType.URL) {
            return Indicators.hostOf(value).filter(h -> h.type() == IndicatorType.DOMAIN)
                    .map(h -> protectedDomains.isProtected(h.value())).orElse(false);
        }
        return false;
    }

    private VtVerdict lookup(IndicatorType type, String value) {
        return switch (type) {
            case URL -> virusTotal.lookupUrl(value);
            case DOMAIN -> virusTotal.lookupDomain(value);
            case IP -> virusTotal.lookupIp(value);
            case FILE_HASH -> virusTotal.lookupFile(value);
            case EMAIL, PHONE -> VtVerdict.pending(); // VirusTotal knows nothing about senders
        };
    }

    private Optional<ThreatRule> globalBlock(IndicatorType type, String value) {
        Instant now = clock.instant();
        return threatRules.candidates(type, value, null, null).stream()
                .filter(ThreatRule::isGlobal)
                .filter(r -> r.getListType() == ListType.BLACKLIST && !r.isExpired(now))
                .findFirst();
    }

    private List<ConsumerReport> openReports(IndicatorType type, String value) {
        return reports.findByIndicatorTypeAndIndicatorValue(type, value).stream()
                .filter(r -> r.getStatus() == Status.WAITING || r.getStatus() == Status.PROTECTED)
                .toList();
    }

    int reportersNeeded() {
        return properties.reportersToBlock();
    }

    long reporters(IndicatorType type, String value) {
        return reports.verifiedReporters(type, value, clock.instant().minus(properties.reportWindow()));
    }

    private static Map<String, Object> reason(String promotedBy, List<ConsumerReport> open) {
        Map<String, Object> reason = new LinkedHashMap<>();
        reason.put("promotedBy", promotedBy);
        reason.put("consumerIds", open.stream().map(r -> r.getConsumerId().toString()).distinct().toList());
        return reason;
    }

    private Report dto(ConsumerReport r) {
        return new Report(r.getId(), r.getIndicatorType(), r.getIndicatorValue(), r.getThreatCategory(), r.getNote(),
                r.getStatus(), reporters(r.getIndicatorType(), r.getIndicatorValue()), properties.reportersToBlock(),
                r.getCreatedAt());
    }
}
