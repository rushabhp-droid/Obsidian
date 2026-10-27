package com.obsidian.b2c;

import com.obsidian.b2c.B2cDtos.Activity;
import com.obsidian.b2c.B2cDtos.Blocklist;
import com.obsidian.b2c.B2cDtos.FileScanRequest;
import com.obsidian.b2c.B2cDtos.MessageScanRequest;
import com.obsidian.b2c.B2cDtos.Report;
import com.obsidian.b2c.B2cDtos.ReportRequest;
import com.obsidian.b2c.B2cDtos.Rule;
import com.obsidian.b2c.B2cDtos.RuleRequest;
import com.obsidian.b2c.B2cDtos.ScanResult;
import com.obsidian.b2c.B2cDtos.Status;
import com.obsidian.b2c.B2cDtos.UrlScanRequest;
import com.obsidian.config.ObsidianProperties;
import com.obsidian.model.IndicatorType;
import com.obsidian.rules.Indicators;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The B2C app's protection API: scans, Activity, personal rules, the firewall blocklist and reports. */
@RestController
@RequestMapping("/api/b2c/v1")
public class ConsumerApiController {

    private static final int ACTIVITY_LIMIT = 500;

    private final ConsumerScanService scans;
    private final ConsumerReportService reports;
    private final ConsumerRuleRepository rules;
    private final ConsumerActivityRepository activity;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObsidianProperties obsidian;
    private final B2cProperties properties;
    private final Clock clock;

    public ConsumerApiController(ConsumerScanService scans, ConsumerReportService reports, ConsumerRuleRepository rules,
            ConsumerActivityRepository activity, JdbcTemplate jdbc, StringRedisTemplate redis,
            ObsidianProperties obsidian, B2cProperties properties, Clock clock) {
        this.scans = scans;
        this.reports = reports;
        this.rules = rules;
        this.activity = activity;
        this.jdbc = jdbc;
        this.redis = redis;
        this.obsidian = obsidian;
        this.properties = properties;
        this.clock = clock;
    }

    /** No sign-in: the app's connectivity card and sign-in screen (Google client ID) read it. */
    @GetMapping("/status")
    public Status status() {
        return new Status("UP", check(() -> jdbc.queryForObject("select 1", Integer.class)),
                check(() -> redis.hasKey("b2c:status-probe")), obsidian.virustotal().enabled(),
                properties.googleConfigured(), properties.googleConfigured() ? properties.googleClientId() : null,
                properties.mail().configured() || properties.logCodes(), clock.instant());
    }

    @PostMapping("/scan/url")
    public ScanResult scanUrl(Consumer consumer, @Valid @RequestBody UrlScanRequest body) {
        return scans.scanUrl(consumer, body.url(), body.source());
    }

    @PostMapping("/scan/file")
    public ScanResult scanFile(Consumer consumer, @Valid @RequestBody FileScanRequest body) {
        return scans.scanFile(consumer, body.sha256(), body.fileName());
    }

    @PostMapping("/scan/message")
    public ScanResult scanMessage(Consumer consumer, @Valid @RequestBody MessageScanRequest body) {
        return scans.scanMessage(consumer, body);
    }

    @GetMapping("/activity")
    public List<Activity> activity(Consumer consumer) {
        return activity.findByConsumerIdOrderByCreatedAtDesc(consumer.getId(), Limit.of(ACTIVITY_LIMIT)).stream()
                .map(Activity::of).toList();
    }

    @GetMapping("/rules")
    public List<Rule> rules(Consumer consumer) {
        return rules.findByConsumerIdOrderByCreatedAtDesc(consumer.getId()).stream().map(Rule::of).toList();
    }

    /** Adds a personal rule, or moves an existing one to the other list. */
    @PostMapping("/rules")
    public Rule addRule(Consumer consumer, @Valid @RequestBody RuleRequest body) {
        String value = Indicators.requireValid(body.indicatorType(), body.indicatorValue());
        ConsumerRule rule = rules.findByConsumerIdAndIndicatorTypeAndIndicatorValue(consumer.getId(),
                body.indicatorType(), value).orElseGet(() ->
                new ConsumerRule(consumer.getId(), body.indicatorType(), value, body.listType()));
        rule.setListType(body.listType());
        return Rule.of(rules.save(rule));
    }

    /** Removes a personal rule. The shared blocklist is unaffected (a report stays a report). */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(Consumer consumer, @PathVariable UUID id) {
        ConsumerRule rule = rules.findByIdAndConsumerId(id, consumer.getId())
                .orElseThrow(() -> B2cException.notFound("No such rule."));
        rules.delete(rule);
        return ResponseEntity.noContent().build();
    }

    /** What the phone firewall enforces: global DOMAIN and IP blocks plus the consumer's own rules. */
    @GetMapping("/firewall/blocklist")
    public Blocklist blocklist(Consumer consumer) {
        List<String> domains = globalBlocks(IndicatorType.DOMAIN);
        List<String> ips = globalBlocks(IndicatorType.IP);
        return new Blocklist(domains, ips, rules(consumer), clock.instant());
    }

    @PostMapping("/reports")
    public Report report(Consumer consumer, @Valid @RequestBody ReportRequest body) {
        return reports.report(consumer, body.indicatorType(), body.indicatorValue(), body.threatCategory(), body.note());
    }

    @GetMapping("/reports")
    public List<Report> myReports(Consumer consumer) {
        return reports.mine(consumer);
    }

    private List<String> globalBlocks(IndicatorType type) {
        return jdbc.queryForList("""
                select indicator_value from threat_rules
                where org_id is null and user_id is null and list_type = 'BLACKLIST' and indicator_type = ?
                  and (expires_at is null or expires_at > now())
                order by indicator_value""", String.class, type.name());
    }

    private static String check(Runnable probe) {
        try {
            probe.run();
            return "UP";
        } catch (RuntimeException e) {
            return "DOWN";
        }
    }
}
