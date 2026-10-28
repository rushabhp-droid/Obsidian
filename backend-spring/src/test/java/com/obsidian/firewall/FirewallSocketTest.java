package com.obsidian.firewall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.obsidian.auth.Tokens;
import com.obsidian.model.EndpointAgent;
import com.obsidian.model.EndpointAgentRepository;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.RuleSource;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.ThreatRule;
import com.obsidian.rules.RuleService;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;

/** /ws/firewall as a daemon sees it (docs/protocol.md). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FirewallSocketTest extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired RuleService rules;
    @Autowired TestAccounts accounts;
    @Autowired EndpointAgentRepository agents;
    @Autowired JdbcTemplate jdbc;

    Team team;
    String token;
    EndpointAgent agent;

    @BeforeEach
    void registerAgent() {
        team = accounts.team(true);
        token = Tokens.generate("obs_");
        agent = agents.save(new EndpointAgent(team.org().getId(), "build-linux-01", Tokens.sha256Hex(token)));
    }

    SocketClient daemon(String bearer) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (bearer != null) {
            headers.add("Authorization", "Bearer " + bearer);
        }
        return SocketClient.connect("ws://localhost:" + port + "/ws/firewall", headers);
    }

    static String host(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8) + ".example";
    }

    static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void handshakeWithoutAValidTokenIsRefused() {
        assertThatThrownBy(() -> daemon(null)).hasMessageContaining("401");
        assertThatThrownBy(() -> daemon("obs_" + "0".repeat(64))).hasMessageContaining("401");
    }

    @Test
    void connectGetsTheFullBlockSetThenLiveChanges() throws Exception {
        String early = host("before-connect");
        rules.report(team.employee(), IndicatorType.DOMAIN, early, ThreatCategory.PHISHING);
        rules.report(team.admin(), IndicatorType.IP, "203.0.113.45", ThreatCategory.MALWARE);

        try (SocketClient daemon = daemon(token)) {
            JsonNode sync = daemon.await(frame -> frame.path("type").asText().equals("SYNC"));
            List<String> synced = new ArrayList<>();
            sync.path("rules").forEach(rule -> {
                assertThat(names(rule)).containsExactlyInAnyOrder("ruleId", "indicator_type", "value");
                synced.add(rule.path("indicator_type").asText() + " " + rule.path("value").asText());
            });
            assertThat(synced).containsExactlyInAnyOrder("DOMAIN " + early, "IP 203.0.113.45");

            String live = host("reported-live");
            rules.report(team.employee(), IndicatorType.DOMAIN, live, ThreatCategory.PHISHING);
            JsonNode block = daemon.await(frame -> frame.path("value").asText().equals(live));
            assertThat(block.path("type").asText()).isEqualTo("BLOCK");
            assertThat(names(block)).containsExactlyInAnyOrder("type", "ruleId", "indicator_type", "value");

            rules.bypass(team.admin(), IndicatorType.DOMAIN, live, null);
            daemon.await(frame -> frame.path("type").asText().equals("BYPASS") && frame.path("value").asText().equals(live));
        }
    }

    @Test
    void personalRulesAndOtherOrganizationsNeverReachTheDaemon() throws Exception {
        Team other = accounts.team(true);
        try (SocketClient daemon = daemon(token)) {
            daemon.await(frame -> frame.path("type").asText().equals("SYNC"));

            rules.report(other.employee(), IndicatorType.DOMAIN, host("other-org"), ThreatCategory.PHISHING);
            rules.report(team.employee(), IndicatorType.URL, "http://" + host("url-only") + "/x", ThreatCategory.PHISHING);
            String marker = host("marker");
            rules.report(team.employee(), IndicatorType.DOMAIN, marker, ThreatCategory.PHISHING);
            rules.bypass(team.colleague(), IndicatorType.DOMAIN, marker, 15);
            String last = host("last");
            rules.report(team.admin(), IndicatorType.DOMAIN, last, ThreatCategory.PHISHING);

            daemon.await(frame -> frame.path("value").asText().equals(last));
            assertThat(daemon.frames).extracting(frame -> frame.path("type").asText() + " " + frame.path("value").asText())
                    .containsExactly("SYNC ", "BLOCK " + marker, "BLOCK " + last);
        }
    }

    @Test
    void globalBlocksGoOutLiveUnlessTheOrganizationAllowsThem() throws Exception {
        String allowed = host("admin-allowed");
        rules.bypass(team.admin(), IndicatorType.DOMAIN, allowed, null);
        try (SocketClient daemon = daemon(token)) {
            daemon.await(frame -> frame.path("type").asText().equals("SYNC"));

            String crowd = host("consumer-crowd");
            ThreatRule global = rules.blockGlobally(null, IndicatorType.DOMAIN, crowd, ThreatCategory.PHISHING,
                    RuleSource.USER_REPORT, null);
            rules.blockGlobally(null, IndicatorType.DOMAIN, allowed, ThreatCategory.PHISHING, RuleSource.USER_REPORT, null);
            String last = host("last");
            rules.report(team.admin(), IndicatorType.DOMAIN, last, ThreatCategory.PHISHING);

            daemon.await(frame -> frame.path("value").asText().equals(last));
            assertThat(daemon.frames).extracting(frame -> frame.path("type").asText() + " " + frame.path("value").asText())
                    .containsExactly("SYNC ", "BLOCK " + crowd, "BLOCK " + last);
            assertThat(daemon.frames.get(1).path("ruleId").asText()).isEqualTo(global.getId().toString());
        } finally {
            jdbc.update("delete from threat_rules where org_id is null and user_id is null");
        }
    }

    @Test
    void heartbeatUpdatesTheAgent() throws Exception {
        try (SocketClient daemon = daemon(token)) {
            daemon.await(frame -> frame.path("type").asText().equals("SYNC"));
            agents.touchHeartbeat(agent.getId(), null);

            daemon.send("{\"type\":\"HEARTBEAT\"}");

            Awaitility.await().atMost(Duration.ofSeconds(5))
                    .until(() -> agents.findById(agent.getId()).orElseThrow().getLastHeartbeatAt() != null);
        }
    }
}
