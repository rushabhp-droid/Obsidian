package com.obsidian.firewall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.obsidian.auth.Tokens;
import com.obsidian.model.EndpointAgent;
import com.obsidian.model.EndpointAgentRepository;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ThreatCategory;
import com.obsidian.rules.RuleService;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketHttpHeaders;

/** /ws/dashboard: session-cookie auth, origin check, org-scoped DashboardEvent frames. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardSocketTest extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired RuleService rules;
    @Autowired TestAccounts accounts;
    @Autowired EndpointAgentRepository agents;

    String sessionCookie(String email) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\""
                        + TestAccounts.PASSWORD + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("__Host-session="))
                .map(c -> c.substring(0, c.indexOf(';'))).findFirst().orElseThrow();
    }

    SocketClient dashboard(String cookie, String origin) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (cookie != null) {
            headers.add("Cookie", cookie);
        }
        headers.setOrigin(origin);
        return SocketClient.connect("ws://localhost:" + port + "/ws/dashboard", headers);
    }

    @Test
    void signedInDashboardGetsItsOrganizationsRuleAndHeartbeatEvents() throws Exception {
        Team team = accounts.team(true);
        Team other = accounts.team(true);
        try (SocketClient browser = dashboard(sessionCookie(team.admin().email()), "http://localhost:3000")) {
            rules.report(other.employee(), IndicatorType.DOMAIN, "other-" + UUID.randomUUID() + ".example", ThreatCategory.PHISHING);
            String domain = "dash-" + UUID.randomUUID().toString().substring(0, 8) + ".example";
            rules.report(team.employee(), IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING);

            JsonNode block = browser.await(frame -> frame.path("value").asText().equals(domain));
            assertThat(block.path("type").asText()).isEqualTo("BLOCK");
            assertThat(block.path("indicator_type").asText()).isEqualTo("DOMAIN");
            assertThat(block.path("at").asText()).isNotBlank();

            String token = Tokens.generate("obs_");
            EndpointAgent agent = agents.save(new EndpointAgent(team.org().getId(), "demo-laptop", Tokens.sha256Hex(token)));
            WebSocketHttpHeaders daemonHeaders = new WebSocketHttpHeaders();
            daemonHeaders.add("Authorization", "Bearer " + token);
            try (SocketClient daemon = SocketClient.connect("ws://localhost:" + port + "/ws/firewall", daemonHeaders)) {
                JsonNode heartbeat = browser.await(frame -> frame.path("type").asText().equals("HEARTBEAT"));
                assertThat(heartbeat.path("agentId").asText()).isEqualTo(agent.getId().toString());
                assertThat(heartbeat.path("hostname").asText()).isEqualTo("demo-laptop");
            }
            assertThat(browser.frames).noneMatch(frame -> frame.path("value").asText().startsWith("other-"));
        }
    }

    @Test
    void withoutASessionTheUpgradeIsRefused() {
        assertThatThrownBy(() -> dashboard(null, "http://localhost:3000")).hasMessageContaining("401");
    }

    @Test
    void foreignOriginIsRefused() throws Exception {
        Team team = accounts.team(true);
        String cookie = sessionCookie(team.admin().email());

        assertThatThrownBy(() -> dashboard(cookie, "https://evil.example")).hasMessageContaining("403");
    }
}
