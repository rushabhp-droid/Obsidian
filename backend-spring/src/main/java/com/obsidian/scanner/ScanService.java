package com.obsidian.scanner;

import com.obsidian.firewall.EventEnvelope.Kind;
import com.obsidian.firewall.EventPublisher;
import com.obsidian.model.Account;
import com.obsidian.model.Channel;
import com.obsidian.model.EmailAttachment;
import com.obsidian.model.EmailLink;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
import com.obsidian.model.ScanLog;
import com.obsidian.model.ScanLogRepository;
import com.obsidian.model.ScannedEmail;
import com.obsidian.model.ScannedEmailRepository;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.ThreatRule;
import com.obsidian.model.Verdict;
import com.obsidian.rules.Indicators;
import com.obsidian.rules.RuleService;
import com.obsidian.scanner.ScanResponse.IndicatorResult;
import com.obsidian.scanner.ScanResponse.Source;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The one scanning pipeline for every channel. Each indicator is resolved in
 * this order: threat_rules with precedence (a URL as itself, then its host; an
 * email sender as the address, then its domain), then VirusTotal behind the
 * Redis cache. One scan_logs row per request, published to dashboards.
 */
@Service
public class ScanService {

    private static final Set<Channel> SCAN_CHANNELS = EnumSet.of(Channel.SMS, Channel.WHATSAPP, Channel.EMAIL, Channel.BROWSER);
    private static final int MAX_URLS = 20;
    private static final int MAX_HASHES = 20;
    private static final int MAX_TEXT = 5000;
    private static final Comparator<IndicatorResult> SEVERITY = Comparator.comparingInt(r -> r.verdict().ordinal());

    private final RuleService rules;
    private final VirusTotalClient virusTotal;
    private final ScanLogRepository scanLogs;
    private final ScannedEmailRepository scannedEmails;
    private final OfficialDomains officialDomains;
    private final EventPublisher events;

    public ScanService(RuleService rules, VirusTotalClient virusTotal, ScanLogRepository scanLogs,
            ScannedEmailRepository scannedEmails, OfficialDomains officialDomains, EventPublisher events) {
        this.rules = rules;
        this.virusTotal = virusTotal;
        this.scanLogs = scanLogs;
        this.scannedEmails = scannedEmails;
        this.officialDomains = officialDomains;
        this.events = events;
    }

    /**
     * A raw Gmail message (email/ only fetches it). Verified official mail, with
     * SPF + DKIM + DMARC passing at mx.google.com and the sender's domain on the
     * allowlist or whitelisted by a rule, skips link and attachment scanning.
     */
    public ScannedEmail scanEmail(Account account, String gmailMessageId, byte[] raw) {
        MimeEmailParser.ParsedEmail parsed = MimeEmailParser.parse(raw);
        HeaderAuthValidator.AuthResults auth = HeaderAuthValidator.parse(parsed.authenticationResults());
        String sender = parsed.senderAddress();

        Optional<IndicatorResult> senderResult = sender == null ? Optional.empty()
                : resolveSender(account, IndicatorType.EMAIL, sender);
        boolean senderBlocked = senderResult.filter(r -> r.verdict() == Verdict.MALICIOUS).isPresent();
        boolean senderTrusted = senderResult.filter(IndicatorResult::bypassed).isPresent();
        boolean official = auth.allPass() && !senderBlocked
                && (officialDomains.contains(Indicators.domainOfEmail(sender).orElse(null)) || senderTrusted);

        List<IndicatorResult> results = new ArrayList<>();
        List<EmailLink> links = new ArrayList<>();
        for (String url : parsed.links()) {
            IndicatorResult result = official
                    ? new IndicatorResult(IndicatorType.URL, Indicators.normalize(IndicatorType.URL, url),
                            Verdict.CLEAN, null, 0, Source.NONE, null, false)
                    : resolveUrl(account, url);
            results.add(result);
            links.add(new EmailLink(result.value(), result.verdict(), result.vtDetections()));
        }
        List<EmailAttachment> attachments = new ArrayList<>();
        for (MimeEmailParser.Attachment file : parsed.attachments()) {
            IndicatorResult result = official
                    ? new IndicatorResult(IndicatorType.FILE_HASH, file.sha256(), Verdict.CLEAN, null, 0, Source.NONE,
                            null, false)
                    : resolveFile(account, file.sha256());
            results.add(result);
            attachments.add(new EmailAttachment(file.filename(), file.sha256(), result.verdict(), result.vtDetections()));
        }
        senderResult.ifPresent(results::add);

        record(account, Channel.EMAIL, sender, results);
        ScannedEmail email = new ScannedEmail(account.userId(), gmailMessageId);
        email.setFromHeader(parsed.from());
        email.setSenderAddress(sender);
        email.setSubject(parsed.subject());
        email.setReceivedAt(parsed.receivedAt());
        email.setSpf(auth.spf());
        email.setDkim(auth.dkim());
        email.setDmarc(auth.dmarc());
        email.setVerifiedOfficial(official);
        email.setVerdict(worst(results));
        email.setLinks(links);
        email.setAttachments(attachments);
        return scannedEmails.save(email);
    }

    public ScanResponse scan(Account account, ScanRequest request) {
        if (request.channel() == null || !SCAN_CHANNELS.contains(request.channel())) {
            throw new InvalidScanRequestException("channel must be SMS, WHATSAPP, EMAIL or BROWSER.");
        }
        boolean hasText = request.text() != null && !request.text().isBlank();
        if (request.isSavedContact() && hasText) {
            throw new InvalidScanRequestException("Saved contacts: send the links only, never the message text.");
        }
        if (hasText && request.text().length() > MAX_TEXT) {
            throw new InvalidScanRequestException("text is limited to " + MAX_TEXT + " characters.");
        }
        List<String> urls = links(request);
        List<String> hashes = hashes(request.fileHashes());

        List<IndicatorResult> results = new ArrayList<>();
        urls.forEach(url -> results.add(resolveUrl(account, url)));
        hashes.forEach(hash -> results.add(resolveFile(account, hash)));
        senderIndicator(request.channel(), request.sender())
                .flatMap(sender -> resolveSender(account, sender.type(), sender.value()))
                .ifPresent(results::add);

        ScanLog log = record(account, request.channel(), request.sender(), results);
        return response(log, results);
    }

    public IndicatorResult resolveUrl(Account account, String url) {
        String value = Indicators.normalize(IndicatorType.URL, url);
        Optional<ThreatRule> rule = rules.decide(account, IndicatorType.URL, value)
                .or(() -> Indicators.hostOf(url).flatMap(host -> rules.decide(account, host.type(), host.value())));
        return rule.map(r -> fromRule(IndicatorType.URL, value, r))
                .orElseGet(() -> fromVirusTotal(IndicatorType.URL, value, virusTotal.lookupUrl(url.trim())));
    }

    public IndicatorResult resolveFile(Account account, String sha256) {
        String value = Indicators.normalize(IndicatorType.FILE_HASH, sha256);
        return rules.decide(account, IndicatorType.FILE_HASH, value)
                .map(r -> fromRule(IndicatorType.FILE_HASH, value, r))
                .orElseGet(() -> fromVirusTotal(IndicatorType.FILE_HASH, value, virusTotal.lookupFile(value)));
    }

    /**
     * A sender is judged by rules only (VirusTotal knows nothing about phone
     * numbers or mailboxes), and only a rule match is reported: an unlisted
     * sender isn't "clean", just unknown.
     */
    public Optional<IndicatorResult> resolveSender(Account account, IndicatorType type, String value) {
        Optional<ThreatRule> rule = rules.decide(account, type, value);
        if (type == IndicatorType.EMAIL) {
            rule = rule.or(() -> Indicators.domainOfEmail(value)
                    .flatMap(domain -> rules.decide(account, IndicatorType.DOMAIN, domain)));
        }
        return rule.map(r -> fromRule(type, value, r));
    }

    /** Looks again at a PENDING email's links and attachments; VirusTotal may have finished by now. */
    public ScannedEmail recheck(Account account, ScannedEmail email) {
        if (email.isVerifiedOfficial()) {
            return email;
        }
        List<IndicatorResult> results = new ArrayList<>();
        List<EmailLink> links = new ArrayList<>();
        for (EmailLink link : email.getLinks()) {
            IndicatorResult result = resolveUrl(account, link.url());
            results.add(result);
            links.add(new EmailLink(link.url(), result.verdict(), result.vtDetections()));
        }
        List<EmailAttachment> attachments = new ArrayList<>();
        for (EmailAttachment file : email.getAttachments()) {
            IndicatorResult result = resolveFile(account, file.sha256());
            results.add(result);
            attachments.add(new EmailAttachment(file.filename(), file.sha256(), result.verdict(), result.vtDetections()));
        }
        if (email.getSenderAddress() != null) {
            resolveSender(account, IndicatorType.EMAIL, email.getSenderAddress()).ifPresent(results::add);
        }
        email.setLinks(links);
        email.setAttachments(attachments);
        email.setVerdict(worst(results));
        return scannedEmails.save(email);
    }

    /** Writes the scan_logs row for one request and publishes it as a SCAN event. */
    public ScanLog record(Account account, Channel channel, String sender, List<IndicatorResult> results) {
        IndicatorResult worst = results.stream().min(SEVERITY).orElse(null);
        Verdict verdict = worst == null ? Verdict.CLEAN : worst.verdict();
        boolean bypassed = verdict == Verdict.CLEAN && results.stream().anyMatch(IndicatorResult::bypassed);
        ScanLog log = scanLogs.save(new ScanLog(account.userId(), channel, truncate(sender),
                worst == null ? null : worst.value(), bypassed ? Verdict.BYPASSED : verdict, maxDetections(results)));
        events.publish(Kind.SCAN, account.orgId(), account.userId(), new ScanEvent(ScanLogDto.of(log)));
        return log;
    }

    static Verdict worst(List<IndicatorResult> results) {
        return results.stream().min(SEVERITY).map(IndicatorResult::verdict).orElse(Verdict.CLEAN);
    }

    private static ScanResponse response(ScanLog log, List<IndicatorResult> results) {
        IndicatorResult worst = results.stream().min(SEVERITY).orElse(null);
        Verdict verdict = worst == null ? Verdict.CLEAN : worst.verdict();
        ThreatCategory category = verdict == Verdict.MALICIOUS || verdict == Verdict.SUSPICIOUS ? worst.category() : null;
        return new ScanResponse(verdict, category, maxDetections(results), log.getId(), List.copyOf(results));
    }

    private static int maxDetections(List<IndicatorResult> results) {
        return results.stream().mapToInt(IndicatorResult::vtDetections).max().orElse(0);
    }

    private static IndicatorResult fromRule(IndicatorType type, String value, ThreatRule rule) {
        boolean blocked = rule.getListType() == ListType.BLACKLIST;
        return new IndicatorResult(type, value, blocked ? Verdict.MALICIOUS : Verdict.CLEAN,
                blocked ? rule.getThreatCategory() : null, 0, Source.RULE, rule.getId(), !blocked);
    }

    private static IndicatorResult fromVirusTotal(IndicatorType type, String value, VtVerdict vt) {
        Source source = vt.verdict() == Verdict.PENDING ? Source.NONE : Source.VIRUSTOTAL;
        return new IndicatorResult(type, value, vt.verdict(), vt.category(), vt.malicious(), source, null, false);
    }

    private static List<String> links(ScanRequest request) {
        Set<String> links = new LinkedHashSet<>();
        if (request.urls() != null) {
            request.urls().forEach(url -> UrlExtractor.clean(url).ifPresent(links::add));
        }
        links.addAll(UrlExtractor.extract(request.text()));
        if (links.size() > MAX_URLS) {
            throw new InvalidScanRequestException("At most " + MAX_URLS + " links per scan.");
        }
        return new ArrayList<>(links);
    }

    private static List<String> hashes(List<String> fileHashes) {
        if (fileHashes == null) {
            return List.of();
        }
        if (fileHashes.size() > MAX_HASHES) {
            throw new InvalidScanRequestException("At most " + MAX_HASHES + " file hashes per scan.");
        }
        List<String> hashes = new ArrayList<>();
        for (String hash : fileHashes) {
            String value = Indicators.normalize(IndicatorType.FILE_HASH, hash);
            if (Indicators.validate(IndicatorType.FILE_HASH, value).isPresent()) {
                throw new InvalidScanRequestException("fileHashes must be SHA-256: 64 hexadecimal characters.");
            }
            hashes.add(value);
        }
        return hashes;
    }

    private record Sender(IndicatorType type, String value) {}

    private static Optional<Sender> senderIndicator(Channel channel, String sender) {
        if (sender == null || sender.isBlank() || channel == Channel.BROWSER) {
            return Optional.empty();
        }
        IndicatorType type = channel == Channel.EMAIL ? IndicatorType.EMAIL : IndicatorType.PHONE;
        String value = Indicators.normalize(type, sender);
        // WhatsApp shows saved contacts by display name: nothing to look up then.
        return Indicators.validate(type, value).isEmpty() ? Optional.of(new Sender(type, value)) : Optional.empty();
    }

    private static String truncate(String sender) {
        return sender == null || sender.length() <= 255 ? sender : sender.substring(0, 255);
    }
}
