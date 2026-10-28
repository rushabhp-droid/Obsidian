package com.obsidian.reports;

import com.obsidian.model.Account;
import com.obsidian.model.Channel;
import com.obsidian.model.ListType;
import com.obsidian.model.ThreatRule;
import com.obsidian.model.Verdict;
import com.obsidian.rules.RuleService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports: scan verdicts per UTC day and per source over the last {@code days}
 * days, and the rules in force. Admins see their organization's members'
 * scans; everyone else sees their own.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final JdbcTemplate jdbc;
    private final RuleService rules;
    private final Clock clock;

    public ReportController(JdbcTemplate jdbc, RuleService rules, Clock clock) {
        this.jdbc = jdbc;
        this.rules = rules;
        this.clock = clock;
    }

    public record Counts(long total, long malicious, long suspicious, long pending, long clean, long bypassed) {}

    public record Day(String date, Counts counts) {}

    public record Source(Channel channel, Counts counts) {}

    public record RuleCounts(long blacklist, long whitelist, long organization, long global, long personal) {}

    public record Summary(int days, Instant from, Instant to, String scope, Counts totals, List<Day> byDay,
            List<Source> bySource, RuleCounts rules) {}

    @GetMapping("/summary")
    public Summary summary(Account account, @RequestParam(defaultValue = "7") int days) {
        int window = Math.max(1, Math.min(days, 90));
        Instant to = clock.instant();
        LocalDate firstDay = LocalDate.ofInstant(to, ZoneOffset.UTC).minusDays(window - 1L);
        Instant from = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();

        String scope = account.isAdmin() ? "s.user_id in (select id from users where org_id = ?)" : "s.user_id = ?";
        Object owner = account.isAdmin() ? account.orgId() : account.userId();
        Map<LocalDate, long[]> perDay = new LinkedHashMap<>();
        for (int i = 0; i < window; i++) {
            perDay.put(firstDay.plusDays(i), new long[6]);
        }
        Map<Channel, long[]> perSource = new EnumMap<>(Channel.class);
        long[] totals = new long[6];
        jdbc.query("select s.created_at, s.channel, s.verdict from scan_logs s where s.created_at >= ? and " + scope,
                rs -> {
                    LocalDate day = LocalDate.ofInstant(rs.getTimestamp(1).toInstant(), ZoneOffset.UTC);
                    Channel channel = Channel.valueOf(rs.getString(2));
                    int column = column(Verdict.valueOf(rs.getString(3)));
                    for (long[] counts : List.of(perDay.computeIfAbsent(day, d -> new long[6]),
                            perSource.computeIfAbsent(channel, c -> new long[6]), totals)) {
                        counts[0]++;
                        counts[column]++;
                    }
                }, Timestamp.from(from), owner);

        List<Day> byDay = new ArrayList<>();
        perDay.forEach((day, counts) -> byDay.add(new Day(day.toString(), counts(counts))));
        List<Source> bySource = new ArrayList<>();
        perSource.forEach((channel, counts) -> bySource.add(new Source(channel, counts(counts))));

        Instant now = clock.instant();
        List<ThreatRule> visible = rules.visibleRules(account).stream().filter(r -> !r.isExpired(now)).toList();
        RuleCounts ruleCounts = new RuleCounts(
                visible.stream().filter(r -> r.getListType() == ListType.BLACKLIST).count(),
                visible.stream().filter(r -> r.getListType() == ListType.WHITELIST).count(),
                visible.stream().filter(r -> r.getOrgId() != null && r.getUserId() == null).count(),
                visible.stream().filter(ThreatRule::isGlobal).count(),
                visible.stream().filter(r -> r.getUserId() != null).count());
        return new Summary(window, from, to, account.isAdmin() ? "ORGANIZATION" : "USER", counts(totals), byDay,
                bySource, ruleCounts);
    }

    private static int column(Verdict verdict) {
        return switch (verdict) {
            case MALICIOUS -> 1;
            case SUSPICIOUS -> 2;
            case PENDING -> 3;
            case CLEAN -> 4;
            case BYPASSED -> 5;
        };
    }

    private static Counts counts(long[] c) {
        return new Counts(c[0], c[1], c[2], c[3], c[4], c[5]);
    }
}
