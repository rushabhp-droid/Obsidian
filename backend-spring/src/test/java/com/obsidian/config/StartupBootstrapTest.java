package com.obsidian.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.obsidian.auth.Tokens;
import com.obsidian.model.EndpointAgentRepository;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Startup setup for `scripts/build.sh run`: demo rules and the local agent token, created in-process,
 * so it works with Turnstile on (a script can't sign in through it, by design).
 */
class StartupBootstrapTest extends IntegrationTest {

    @Autowired StartupBootstrap bootstrap;
    @Autowired TestAccounts accounts;
    @Autowired EndpointAgentRepository agents;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path dir;

    Team team;

    @BeforeEach
    void team() {
        team = accounts.team(true);
    }

    @Test
    void rulesFileIsAppliedThroughRuleServiceAsEachAccount() throws Exception {
        Path rules = dir.resolve("rules.json");
        Files.writeString(rules, """
                [{"as":"%s","action":"REPORT","indicatorType":"IP","indicatorValue":"203.0.113.45","threatCategory":"MALWARE"},
                 {"as":"%s","action":"REPORT","indicatorType":"DOMAIN","indicatorValue":"parcel-redelivery.example","threatCategory":"PHISHING"},
                 {"as":"%s","action":"BYPASS","indicatorType":"DOMAIN","indicatorValue":"delivery-status.example","ttlMinutes":120}]
                """.formatted(team.admin().email(), team.colleague().email(), team.employee().email()));

        bootstrap.applyRules(rules);
        bootstrap.applyRules(rules);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select indicator_value, source, user_id from threat_rules where org_id = ? order by indicator_value",
                team.org().getId());
        assertThat(rows).extracting(r -> r.get("indicator_value") + " " + r.get("source")).containsExactly(
                "203.0.113.45 ADMIN_POLICY", "delivery-status.example USER_BYPASS", "parcel-redelivery.example USER_REPORT");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where org_id = ?", Integer.class,
                team.org().getId())).isEqualTo(6);
    }

    @Test
    void unknownAccountInTheRulesFileStopsStartup() throws Exception {
        Path rules = dir.resolve("rules.json");
        Files.writeString(rules, """
                [{"as":"nobody@nowhere.example","action":"REPORT","indicatorType":"IP","indicatorValue":"203.0.113.9"}]""");

        assertThatThrownBy(() -> bootstrap.applyRules(rules)).hasMessageContaining("nobody@nowhere.example");
    }

    @Test
    void agentTokenIsWrittenOnceAndKeptWhileTheServerStillKnowsIt() throws Exception {
        Path tokenFile = dir.resolve("agent.token");

        bootstrap.ensureAgentToken(tokenFile, team.admin().email(), "demo-laptop");
        String token = Files.readString(tokenFile).trim();
        bootstrap.ensureAgentToken(tokenFile, team.admin().email(), "demo-laptop");

        assertThat(token).matches("obs_[0-9a-f]{64}");
        assertThat(Files.readString(tokenFile).trim()).isEqualTo(token);
        assertThat(Files.getPosixFilePermissions(tokenFile))
                .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        assertThat(agents.findByTokenHash(Tokens.sha256Hex(token))).get()
                .satisfies(agent -> assertThat(agent.getOrgId()).isEqualTo(team.org().getId()));
        assertThat(agents.findByOrgIdOrderByCreatedAtDesc(team.org().getId())).hasSize(1);
    }

    @Test
    void staleAgentTokenIsReplaced() throws Exception {
        Path tokenFile = dir.resolve("agent.token");
        Files.writeString(tokenFile, "obs_" + "0".repeat(64) + "\n");

        bootstrap.ensureAgentToken(tokenFile, team.admin().email(), "demo-laptop");

        String token = Files.readString(tokenFile).trim();
        assertThat(token).isNotEqualTo("obs_" + "0".repeat(64));
        assertThat(agents.findByTokenHash(Tokens.sha256Hex(token))).isPresent();
    }

    @Test
    void onlyAnAdminCanOwnTheAgent() {
        assertThatThrownBy(() -> bootstrap.ensureAgentToken(dir.resolve("agent.token"), team.employee().email(), "x"))
                .hasMessageContaining("admin");
    }
}
