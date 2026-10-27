package com.obsidian.rules;

import static com.obsidian.model.ListType.BLACKLIST;
import static com.obsidian.model.ListType.WHITELIST;
import static com.obsidian.model.RuleSource.ADMIN_POLICY;
import static com.obsidian.model.RuleSource.USER_BYPASS;
import static com.obsidian.model.RuleSource.USER_REPORT;
import static com.obsidian.model.RuleSource.VIRUSTOTAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
import com.obsidian.model.RuleSource;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.ThreatRule;
import com.obsidian.rules.RulePrecedence.Scope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TODO.md §4.B: the first match wins, in this order. */
class RulePrecedenceTest {

    static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    static final UUID ORG = UUID.fromString("6f1c2a4e-0b7d-4c55-9a51-2d8e5b3c9f10");
    static final UUID OTHER_ORG = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    static final UUID USER = UUID.fromString("b2e4d6f8-1a3c-4e5f-8a9b-0c1d2e3f4a5b");
    static final UUID OTHER_USER = UUID.fromString("00000000-0000-4000-8000-0000000000bb");

    static final Scope EMPLOYEE = new Scope(ORG, USER, true);
    static final Scope EMPLOYEE_NO_BYPASS = new Scope(ORG, USER, false);
    static final Scope CONSUMER = new Scope(null, USER, true);
    static final Scope ORG_ONLY = Scope.organization(ORG, true);

    static int ids;

    static ThreatRule rule(UUID orgId, UUID userId, ListType list, RuleSource source, Instant expiresAt) {
        UUID id = new UUID(0, ++ids);
        return new ThreatRule(id, orgId, userId, IndicatorType.DOMAIN, "login-verify.example", list,
                list == WHITELIST ? ThreatCategory.SAFE : ThreatCategory.PHISHING, source, 100, expiresAt, NOW);
    }

    static Optional<ThreatRule> resolve(Scope scope, ThreatRule... rules) {
        return RulePrecedence.resolve(List.of(rules), scope, NOW);
    }

    @Test
    void adminPolicyWhitelistBeatsTheUsersOwnBlacklist() {
        ThreatRule admin = rule(ORG, null, WHITELIST, ADMIN_POLICY, null);
        ThreatRule userBlock = rule(ORG, USER, BLACKLIST, USER_REPORT, null);

        assertThat(resolve(EMPLOYEE, userBlock, admin)).contains(admin);
    }

    @Test
    void adminPolicyBlacklistBeatsAUserBypass() {
        ThreatRule admin = rule(ORG, null, BLACKLIST, ADMIN_POLICY, null);
        ThreatRule bypass = rule(ORG, USER, WHITELIST, USER_BYPASS, NOW.plusSeconds(600));

        assertThat(resolve(EMPLOYEE, bypass, admin)).contains(admin);
    }

    @Test
    void userBypassBeatsAColleaguesReport() {
        ThreatRule report = rule(ORG, null, BLACKLIST, USER_REPORT, null);
        ThreatRule bypass = rule(ORG, USER, WHITELIST, USER_BYPASS, NOW.plusSeconds(600));

        assertThat(resolve(EMPLOYEE, report, bypass)).contains(bypass);
    }

    @Test
    void userBypassIsIgnoredWhenTheOrgDisallowsIt() {
        ThreatRule report = rule(ORG, null, BLACKLIST, USER_REPORT, null);
        ThreatRule bypass = rule(ORG, USER, WHITELIST, USER_BYPASS, NOW.plusSeconds(600));

        assertThat(resolve(EMPLOYEE_NO_BYPASS, report, bypass)).contains(report);
    }

    @Test
    void expiredBypassIsIgnored() {
        ThreatRule report = rule(ORG, null, BLACKLIST, USER_REPORT, null);
        ThreatRule expired = rule(ORG, USER, WHITELIST, USER_BYPASS, NOW.minusSeconds(1));

        assertThat(resolve(EMPLOYEE, report, expired)).contains(report);
    }

    @Test
    void enterpriseBypassWithoutExpiryIsIgnored() {
        ThreatRule report = rule(ORG, null, BLACKLIST, USER_REPORT, null);
        ThreatRule permanent = rule(ORG, USER, WHITELIST, USER_BYPASS, null);

        assertThat(resolve(EMPLOYEE, report, permanent)).contains(report);
    }

    @Test
    void consumerBypassMayBePermanent() {
        ThreatRule global = rule(null, null, BLACKLIST, VIRUSTOTAL, null);
        ThreatRule permanent = rule(null, USER, WHITELIST, USER_BYPASS, null);

        assertThat(resolve(CONSUMER, global, permanent)).contains(permanent);
    }

    @Test
    void usersOwnBlacklistBeatsAnOrgWhitelistThatIsNotAdminPolicy() {
        ThreatRule orgAllow = rule(ORG, null, WHITELIST, VIRUSTOTAL, null);
        ThreatRule userBlock = rule(ORG, USER, BLACKLIST, USER_REPORT, null);

        assertThat(resolve(EMPLOYEE, orgAllow, userBlock)).contains(userBlock);
    }

    @Test
    void orgReportBeatsAGlobalWhitelist() {
        ThreatRule global = rule(null, null, WHITELIST, VIRUSTOTAL, null);
        ThreatRule report = rule(ORG, null, BLACKLIST, USER_REPORT, null);

        assertThat(resolve(EMPLOYEE, global, report)).contains(report);
    }

    @Test
    void globalRuleAppliesWhenNothingCloserMatches() {
        ThreatRule global = rule(null, null, BLACKLIST, VIRUSTOTAL, null);

        assertThat(resolve(EMPLOYEE, global)).contains(global);
    }

    @Test
    void rulesOfOtherOrganizationsAndUsersNeverApply() {
        ThreatRule otherOrg = rule(OTHER_ORG, null, BLACKLIST, ADMIN_POLICY, null);
        ThreatRule otherUser = rule(ORG, OTHER_USER, WHITELIST, USER_BYPASS, NOW.plusSeconds(600));

        assertThat(resolve(EMPLOYEE, otherOrg, otherUser)).isEmpty();
    }

    @Test
    void organizationScopeIgnoresUserRules() {
        ThreatRule report = rule(ORG, null, BLACKLIST, USER_REPORT, null);
        ThreatRule bypass = rule(ORG, USER, WHITELIST, USER_BYPASS, NOW.plusSeconds(600));

        assertThat(resolve(ORG_ONLY, report, bypass)).contains(report);
    }

    @Test
    void expiredAdminBypassFallsBackToTheGlobalBlock() {
        ThreatRule global = rule(null, null, BLACKLIST, VIRUSTOTAL, null);
        ThreatRule adminBypass = rule(ORG, null, WHITELIST, ADMIN_POLICY, NOW);

        assertThat(resolve(ORG_ONLY, global, adminBypass)).contains(global);
    }

    @Test
    void noRuleMeansNoDecision() {
        assertThat(resolve(EMPLOYEE)).isEmpty();
    }
}
