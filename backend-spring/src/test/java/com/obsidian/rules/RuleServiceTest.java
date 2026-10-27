package com.obsidian.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.model.Account;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
import com.obsidian.model.RuleSource;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.ThreatRule;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.RedisEvents;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

class RuleServiceTest extends IntegrationTest {

    @Autowired RuleService rules;
    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired RedisConnectionFactory redis;
    @Autowired ObjectMapper mapper;

    RedisEvents events;
    Team team;

    @BeforeEach
    void setUp() {
        events = new RedisEvents(redis, mapper);
        team = accounts.team(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        events.close();
    }

    int rowsFor(String value) {
        return jdbc.queryForObject("select count(*) from threat_rules where indicator_value = ?", Integer.class, value);
    }

    String unique(String host) {
        return host.replace(".example", "-" + System.nanoTime() + ".example");
    }

    @Test
    void employeeReportBlocksForTheWholeOrganization() {
        String domain = unique("login-verify.example");

        ThreatRule rule = rules.report(team.employee(), IndicatorType.DOMAIN, domain.toUpperCase(), ThreatCategory.PHISHING);

        assertThat(rule.getIndicatorValue()).isEqualTo(domain);
        assertThat(rule.getOrgId()).isEqualTo(team.org().getId());
        assertThat(rule.getUserId()).isNull();
        assertThat(rule.getListType()).isEqualTo(ListType.BLACKLIST);
        assertThat(rule.getSource()).isEqualTo(RuleSource.USER_REPORT);
        assertThat(rules.decide(team.colleague(), IndicatorType.DOMAIN, domain)).get()
                .extracting(ThreatRule::getId).isEqualTo(rule.getId());
    }

    @Test
    void adminBypassFlipsTheSameRowInsteadOfAddingOne() {
        String domain = unique("delivery-status.example");
        ThreatRule reported = rules.report(team.employee(), IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING);

        ThreatRule bypassed = rules.bypass(team.admin(), IndicatorType.DOMAIN, domain, null);

        assertThat(bypassed.getId()).isEqualTo(reported.getId());
        assertThat(bypassed.getListType()).isEqualTo(ListType.WHITELIST);
        assertThat(bypassed.getThreatCategory()).isEqualTo(ThreatCategory.SAFE);
        assertThat(bypassed.getSource()).isEqualTo(RuleSource.ADMIN_POLICY);
        assertThat(rowsFor(domain)).isEqualTo(1);
    }

    @Test
    void employeeCannotOverrideAnAdminPolicy() {
        String domain = unique("docs.aster.example");
        rules.bypass(team.admin(), IndicatorType.DOMAIN, domain, null);

        assertThatThrownBy(() -> rules.report(team.employee(), IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING))
                .isInstanceOf(RuleConflictException.class);
        assertThat(jdbc.queryForObject("select list_type from threat_rules where indicator_value = ?", String.class, domain))
                .isEqualTo("WHITELIST");
    }

    @Test
    void employeeBypassIsPersonalAndExpires() {
        String domain = unique("parcel-track.example");
        ThreatRule report = rules.report(team.colleague(), IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING);

        ThreatRule bypass = rules.bypass(team.employee(), IndicatorType.DOMAIN, domain, 15);

        assertThat(bypass.getUserId()).isEqualTo(team.employee().userId());
        assertThat(bypass.getOrgId()).isEqualTo(team.org().getId());
        assertThat(bypass.getSource()).isEqualTo(RuleSource.USER_BYPASS);
        assertThat(bypass.getExpiresAt()).isCloseTo(Instant.now().plus(15, ChronoUnit.MINUTES), within(1, ChronoUnit.MINUTES));
        assertThat(rules.decide(team.employee(), IndicatorType.DOMAIN, domain)).get()
                .extracting(ThreatRule::getId).isEqualTo(bypass.getId());
        assertThat(rules.decide(team.colleague(), IndicatorType.DOMAIN, domain)).get()
                .extracting(ThreatRule::getId).isEqualTo(report.getId());
    }

    @Test
    void employeeBypassNeedsATtl() {
        assertThatThrownBy(() -> rules.bypass(team.employee(), IndicatorType.DOMAIN, unique("x.example"), null))
                .isInstanceOf(InvalidRuleRequestException.class);
    }

    @Test
    void ttlOutsideOneMinuteToOneDayIsRejected() {
        assertThatThrownBy(() -> rules.bypass(team.employee(), IndicatorType.DOMAIN, unique("x.example"), 0))
                .isInstanceOf(InvalidRuleRequestException.class);
        assertThatThrownBy(() -> rules.bypass(team.admin(), IndicatorType.DOMAIN, unique("x.example"), 1441))
                .isInstanceOf(InvalidRuleRequestException.class);
    }

    @Test
    void employeeBypassIsRefusedWhenTheOrgDisallowsIt() {
        Team strict = accounts.team(false);

        assertThatThrownBy(() -> rules.bypass(strict.employee(), IndicatorType.DOMAIN, unique("x.example"), 15))
                .isInstanceOf(BypassNotAllowedException.class);
    }

    @Test
    void employeeBypassIsRefusedWhenAnAdminPolicyBlocks() {
        String ip = "203.0.113." + (System.nanoTime() % 200 + 1);
        rules.report(team.admin(), IndicatorType.IP, ip, ThreatCategory.MALWARE);

        assertThatThrownBy(() -> rules.bypass(team.employee(), IndicatorType.IP, ip, 15))
                .isInstanceOf(RuleConflictException.class);
    }

    @Test
    void consumerRulesStayInTheirOwnScope() {
        Account consumer = accounts.consumer();
        String phone = "+1555" + (System.nanoTime() % 1_000_000);

        ThreatRule rule = rules.report(consumer, IndicatorType.PHONE, phone, ThreatCategory.SPAM);

        assertThat(rule.getOrgId()).isNull();
        assertThat(rule.getUserId()).isEqualTo(consumer.userId());
        assertThat(rules.decide(accounts.consumer(), IndicatorType.PHONE, phone)).isEmpty();
    }

    @Test
    void invalidIndicatorWritesNothing() {
        assertThatThrownBy(() -> rules.report(team.admin(), IndicatorType.DOMAIN, "evil.example\n0.0.0.0 bank.example",
                ThreatCategory.PHISHING)).isInstanceOf(InvalidIndicatorException.class);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where org_id = ?", Integer.class,
                team.org().getId())).isZero();
    }

    @Test
    void everyChangeIsAudited() {
        String domain = unique("audit-me.example");
        ThreatRule reported = rules.report(team.employee(), IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING);
        rules.bypass(team.admin(), IndicatorType.DOMAIN, domain, null);

        List<Map<String, Object>> audit = jdbc.queryForList("""
                select action, actor_user_id, org_id, details ->> 'listType' as list,
                       details ->> 'previousListType' as previous, details ->> 'indicatorValue' as value
                from audit_logs where rule_id = ? order by created_at""", reported.getId());

        assertThat(audit).hasSize(2);
        assertThat(audit.get(0)).containsEntry("action", "REPORT").containsEntry("actor_user_id", team.employee().userId())
                .containsEntry("list", "BLACKLIST").containsEntry("previous", null).containsEntry("value", domain)
                .containsEntry("org_id", team.org().getId());
        assertThat(audit.get(1)).containsEntry("action", "BYPASS").containsEntry("actor_user_id", team.admin().userId())
                .containsEntry("list", "WHITELIST").containsEntry("previous", "BLACKLIST");
    }

    @Test
    void everyChangeIsPublishedOnRedis() {
        String domain = unique("publish-me.example");
        ThreatRule rule = rules.report(team.employee(), IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING);

        JsonNode envelope = events.await(e -> e.path("event").path("value").asText().equals(domain));

        assertThat(envelope.path("kind").asText()).isEqualTo("RULE");
        assertThat(envelope.path("orgId").asText()).isEqualTo(team.org().getId().toString());
        assertThat(envelope.path("userId").isNull()).isTrue();
        assertThat(envelope.path("event").path("type").asText()).isEqualTo("BLOCK");
        assertThat(envelope.path("event").path("ruleId").asText()).isEqualTo(rule.getId().toString());
        assertThat(envelope.path("event").path("indicator_type").asText()).isEqualTo("DOMAIN");
        assertThat(envelope.path("event").path("at").asText()).isNotBlank();
    }

    @Test
    void daemonBlockSetHoldsOrgAndGlobalBlocksAfterPrecedence() {
        String blocked = unique("sync-block.example");
        String lifted = unique("sync-lifted.example");
        String personal = unique("sync-personal.example");
        rules.report(team.employee(), IndicatorType.DOMAIN, blocked, ThreatCategory.PHISHING);
        rules.report(team.employee(), IndicatorType.DOMAIN, lifted, ThreatCategory.PHISHING);
        rules.bypass(team.admin(), IndicatorType.DOMAIN, lifted, null);
        rules.report(team.employee(), IndicatorType.DOMAIN, personal, ThreatCategory.PHISHING);
        rules.bypass(team.colleague(), IndicatorType.DOMAIN, personal, 15);
        rules.report(team.employee(), IndicatorType.URL, "http://" + blocked + "/reset", ThreatCategory.PHISHING);

        List<String> values = rules.firewallBlocks(team.org().getId()).stream().map(FirewallRule::value).toList();

        assertThat(values).contains(blocked, personal).doesNotContain(lifted, "http://" + blocked + "/reset");
    }

    @Test
    void visibleRulesExcludeOtherOrganizationsAndExpiredRows() {
        Team other = accounts.team(true);
        String mine = unique("mine.example");
        String theirs = unique("theirs.example");
        rules.report(team.employee(), IndicatorType.DOMAIN, mine, ThreatCategory.PHISHING);
        rules.report(other.employee(), IndicatorType.DOMAIN, theirs, ThreatCategory.PHISHING);
        String expiring = unique("expired.example");
        rules.bypass(team.employee(), IndicatorType.DOMAIN, expiring, 1);
        jdbc.update("update threat_rules set expires_at = now() - interval '1 minute' where indicator_value = ?", expiring);

        List<String> visible = rules.visibleRules(team.employee()).stream().map(ThreatRule::getIndicatorValue).toList();

        assertThat(visible).contains(mine).doesNotContain(theirs, expiring);
    }
}
