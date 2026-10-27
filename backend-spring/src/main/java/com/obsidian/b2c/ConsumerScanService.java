package com.obsidian.b2c;

import com.obsidian.b2c.B2cDtos.ScanResult;
import com.obsidian.b2c.ConsumerActivity.Kind;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.ThreatRule;
import com.obsidian.model.Verdict;
import com.obsidian.rules.Indicators;
import com.obsidian.rules.RulePrecedence;
import com.obsidian.rules.ThreatRuleRepository;
import com.obsidian.scanner.ScanResponse.IndicatorResult;
import com.obsidian.scanner.ScanResponse.Source;
import com.obsidian.scanner.UrlExtractor;
import com.obsidian.scanner.VirusTotalClient;
import com.obsidian.scanner.VtVerdict;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Link, file and message checks for the B2C app. Each indicator is decided by,
 * in order: the consumer's own whitelist/blacklist, the shared global rules in
 * threat_rules, then VirusTotal. A URL is also matched by its host, and a domain
 * by its parent domains (login.example.com matches a rule for example.com).
 * Unknown stays PENDING, never CLEAN. Each check is one Activity entry.
 */
@Service
public class ConsumerScanService {

    static final int MAX_URLS = 20;
    private static final Comparator<IndicatorResult> SEVERITY = Comparator.comparingInt(r -> r.verdict().ordinal());

    /** How a consumer's indicator was decided. */
    record Decision(IndicatorResult result, String decidedBy) {}

    private final ConsumerRuleRepository consumerRules;
    private final ThreatRuleRepository threatRules;
    private final VirusTotalClient virusTotal;
    private final ConsumerActivityRepository activity;
    private final Clock clock;

    public ConsumerScanService(ConsumerRuleRepository consumerRules, ThreatRuleRepository threatRules,
            VirusTotalClient virusTotal, ConsumerActivityRepository activity, Clock clock) {
        this.consumerRules = consumerRules;
        this.threatRules = threatRules;
        this.virusTotal = virusTotal;
        this.activity = activity;
        this.clock = clock;
    }

    public ScanResult scanUrl(Consumer consumer, String rawUrl, Kind source) {
        String url = UrlExtractor.clean(rawUrl)
                .orElseThrow(() -> B2cException.badRequest("Enter a full web address, such as https://example.com."));
        Decision decision = decideUrl(consumer.getId(), url);
        Kind kind = source == Kind.LINK_OPENED ? Kind.LINK_OPENED : Kind.URL_SCAN;
        return record(consumer, kind, url, null, List.of(decision));
    }

    public ScanResult scanFile(Consumer consumer, String sha256, String fileName) {
        String hash = Indicators.normalize(IndicatorType.FILE_HASH, sha256);
        Decision decision = rule(consumer.getId(), IndicatorType.FILE_HASH, List.of(hash))
                .orElseGet(() -> fromVirusTotal(IndicatorType.FILE_HASH, hash, virusTotal.lookupFile(hash)));
        String subject = fileName == null || fileName.isBlank() ? hash : fileName.trim();
        return record(consumer, Kind.FILE_SCAN, subject, hash, List.of(decision));
    }

    /**
     * A messaging notification. Saved contacts send links only; message text is
     * read for links and then dropped, never stored.
     */
    public ScanResult scanMessage(Consumer consumer, B2cDtos.MessageScanRequest request) {
        boolean hasText = request.text() != null && !request.text().isBlank();
        if (request.savedContact() && hasText) {
            throw B2cException.badRequest("Saved contacts: send the links only, never the message text.");
        }
        Set<String> links = new LinkedHashSet<>();
        if (request.urls() != null) {
            request.urls().forEach(url -> UrlExtractor.clean(url).ifPresent(links::add));
        }
        links.addAll(UrlExtractor.extract(request.text()));
        if (links.size() > MAX_URLS) {
            throw B2cException.badRequest("At most " + MAX_URLS + " links per message.");
        }
        List<Decision> decisions = new ArrayList<>();
        links.forEach(url -> decisions.add(decideUrl(consumer.getId(), url)));
        senderIndicator(request.sender())
                .flatMap(sender -> rule(consumer.getId(), sender.type(), List.of(sender.value())))
                .ifPresent(decisions::add);
        String sender = request.sender() == null || request.sender().isBlank() ? "Unknown sender" : request.sender().trim();
        return record(consumer, Kind.MESSAGE, truncate(sender, 255), truncate(request.app(), 255), decisions);
    }

    /** The URL as itself, then its host and the host's parent domains. */
    public Decision decideUrl(UUID consumerId, String url) {
        String value = Indicators.normalize(IndicatorType.URL, url);
        Optional<Decision> byRule = rule(consumerId, IndicatorType.URL, List.of(value))
                .or(() -> Indicators.hostOf(url).flatMap(host -> rule(consumerId, host.type(),
                        host.type() == IndicatorType.DOMAIN ? domainAndParents(host.value()) : List.of(host.value()))))
                .map(d -> new Decision(relabel(d.result(), IndicatorType.URL, value), d.decidedBy()));
        return byRule.orElseGet(() -> fromVirusTotal(IndicatorType.URL, value, virusTotal.lookupUrl(url.trim())));
    }

    /**
     * Personal rules first (any of {@code values}, most specific first), then global
     * threat_rules. Only global rules count from threat_rules: org rules are private
     * to the organization.
     */
    Optional<Decision> rule(UUID consumerId, IndicatorType type, List<String> values) {
        for (String value : values) {
            Optional<ConsumerRule> personal =
                    consumerRules.findByConsumerIdAndIndicatorTypeAndIndicatorValue(consumerId, type, value);
            if (personal.isPresent()) {
                boolean blocked = personal.get().getListType() == ListType.BLACKLIST;
                return Optional.of(new Decision(new IndicatorResult(type, value,
                        blocked ? Verdict.MALICIOUS : Verdict.CLEAN, blocked ? ThreatCategory.MALICIOUS : null, 0,
                        Source.RULE, personal.get().getId(), !blocked), "PERSONAL_RULE"));
            }
        }
        for (String value : values) {
            Optional<ThreatRule> global = RulePrecedence.resolve(threatRules.candidates(type, value, null, null),
                    new RulePrecedence.Scope(null, null, true), clock.instant());
            if (global.isPresent()) {
                boolean blocked = global.get().getListType() == ListType.BLACKLIST;
                return Optional.of(new Decision(new IndicatorResult(type, value,
                        blocked ? Verdict.MALICIOUS : Verdict.CLEAN, blocked ? global.get().getThreatCategory() : null,
                        0, Source.RULE, global.get().getId(), false), "SHARED_RULE"));
            }
        }
        return Optional.empty();
    }

    /** example.com for login.example.com, and so on up to the last two labels. */
    static List<String> domainAndParents(String domain) {
        List<String> values = new ArrayList<>();
        String current = domain;
        values.add(current);
        int dot;
        while ((dot = current.indexOf('.')) > 0 && current.indexOf('.', dot + 1) > 0) {
            current = current.substring(dot + 1);
            values.add(current);
        }
        return values;
    }

    /** 0–100, higher is riskier; null while unknown. */
    static Integer riskScore(IndicatorResult result) {
        return switch (result.verdict()) {
            case MALICIOUS -> result.source() == Source.RULE ? 95 : Math.min(100, 70 + 3 * result.vtDetections());
            case SUSPICIOUS -> Math.min(69, 40 + 10 * result.vtDetections());
            case PENDING -> null;
            case CLEAN, BYPASSED -> 0;
        };
    }

    private ScanResult record(Consumer consumer, Kind kind, String subject, String detail, List<Decision> decisions) {
        Decision worst = decisions.stream().min(Comparator.comparing(Decision::result, SEVERITY)).orElse(null);
        Verdict verdict = worst == null ? Verdict.CLEAN : worst.result().verdict();
        ThreatCategory category = verdict == Verdict.MALICIOUS || verdict == Verdict.SUSPICIOUS
                ? worst.result().category() : null;
        Integer score = worst == null ? Integer.valueOf(0) : riskScore(worst.result());
        int detections = decisions.stream().mapToInt(d -> d.result().vtDetections()).max().orElse(0);
        boolean trusted = verdict == Verdict.CLEAN && decisions.stream().anyMatch(d -> d.result().bypassed());
        ConsumerActivity saved = activity.save(new ConsumerActivity(consumer.getId(), kind, truncate(subject, 2048),
                detail, trusted ? Verdict.BYPASSED : verdict, category, score, detections));
        return new ScanResult(verdict, category, score, detections, trusted,
                worst == null ? "NONE" : worst.decidedBy(),
                decisions.stream().map(Decision::result).toList(), saved.getId());
    }

    private static Decision fromVirusTotal(IndicatorType type, String value, VtVerdict vt) {
        Source source = vt.verdict() == Verdict.PENDING ? Source.NONE : Source.VIRUSTOTAL;
        return new Decision(new IndicatorResult(type, value, vt.verdict(), vt.category(), vt.malicious(), source, null,
                false), source == Source.NONE ? "NONE" : "VIRUSTOTAL");
    }

    private static IndicatorResult relabel(IndicatorResult r, IndicatorType type, String value) {
        return new IndicatorResult(type, value, r.verdict(), r.category(), r.vtDetections(), r.source(), r.ruleId(),
                r.bypassed());
    }

    private record Sender(IndicatorType type, String value) {}

    private static Optional<Sender> senderIndicator(String sender) {
        if (sender == null || sender.isBlank()) {
            return Optional.empty();
        }
        IndicatorType type = sender.contains("@") ? IndicatorType.EMAIL : IndicatorType.PHONE;
        String value = Indicators.normalize(type, sender);
        return Indicators.validate(type, value).isEmpty() ? Optional.of(new Sender(type, value)) : Optional.empty();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
