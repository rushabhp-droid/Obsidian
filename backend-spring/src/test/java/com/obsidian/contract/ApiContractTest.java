package com.obsidian.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.model.AuthCheck;
import com.obsidian.model.Channel;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
import com.obsidian.model.Role;
import com.obsidian.model.RuleSource;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.Verdict;
import com.obsidian.scanner.ScanRequest;
import com.obsidian.scanner.ScanService;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import com.obsidian.support.WebSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The backend's real responses against web-dashboard/src/lib/types.ts. If
 * either side renames or drops a field, this fails (docs/protocol.md, "Contracts").
 */
@AutoConfigureMockMvc
class ApiContractTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper mapper;
    @Autowired ScanService scans;

    final TypesTs types = TypesTs.load();
    Team team;
    WebSession admin;

    @BeforeEach
    void signIn() throws Exception {
        team = accounts.team(true);
        admin = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);
    }

    JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(admin.withCsrf(request)).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString());
    }

    static Set<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }

    @Test
    void enumsMatchTheDashboardUnions() {
        assertThat(types.union("IndicatorType")).isEqualTo(names(IndicatorType.values()));
        assertThat(types.union("ListType")).isEqualTo(names(ListType.values()));
        assertThat(types.union("ThreatCategory")).isEqualTo(names(ThreatCategory.values()));
        assertThat(types.union("RuleSource")).isEqualTo(names(RuleSource.values()));
        assertThat(types.union("Role")).isEqualTo(names(Role.values()));
        assertThat(types.union("AuthCheck")).isEqualTo(names(AuthCheck.values()));
        assertThat(types.union("ScanVerdict")).isEqualTo(names(Verdict.values()));
        // The scan API never returns BYPASSED; only scan_logs stores it.
        assertThat(types.union("Verdict")).isEqualTo(Set.of("MALICIOUS", "SUSPICIOUS", "CLEAN", "PENDING"));
        // CODE is Code Guard [P1]; the dashboard doesn't show it yet.
        assertThat(types.union("Channel")).isEqualTo(Set.of("EMAIL", "SMS", "WHATSAPP", "FIREWALL"));
        assertThat(names(Channel.values())).containsAll(types.union("Channel"));
    }

    @Test
    void sessionInfoMatches() throws Exception {
        JsonNode me = json(get("/api/v1/auth/me"));

        assertThat(keys(me)).isEqualTo(types.fields("SessionInfo"));
        assertThat(keys(me.get("organization"))).isEqualTo(types.nested("SessionInfo", "organization"));
    }

    @Test
    void threatRuleMatchesForReportBypassAndList() throws Exception {
        JsonNode reported = json(post("/api/v1/rules/report").contentType(MediaType.APPLICATION_JSON)
                .content("{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"contract-check.example\"}"));
        JsonNode bypassed = json(post("/api/v1/rules/bypass").contentType(MediaType.APPLICATION_JSON)
                .content("{\"indicatorType\":\"IP\",\"indicatorValue\":\"198.51.100.7\",\"ttlMinutes\":15}"));
        JsonNode listed = json(get("/api/v1/rules"));

        Set<String> expected = types.fields("ThreatRule");
        assertThat(keys(reported)).isEqualTo(expected);
        assertThat(keys(bypassed)).isEqualTo(expected);
        assertThat(listed.isArray()).isTrue();
        assertThat(keys(listed.get(0))).isEqualTo(expected);
        assertThat(reported.get("userId").isNull()).isTrue();
        assertThat(bypassed.get("expiresAt").isTextual()).isTrue();
    }

    @Test
    void scanLogMatches() throws Exception {
        scans.scan(team.admin(), new ScanRequest(Channel.SMS, "+15550100", false, null,
                List.of("http://contract-scan.example/x"), List.of()));

        JsonNode listed = json(get("/api/v1/scans"));

        assertThat(keys(listed.get(0))).isEqualTo(types.fields("ScanLog"));
    }

    @Test
    void scannedEmailAndGmailConnectionMatch() throws Exception {
        byte[] raw;
        try (var in = getClass().getResourceAsStream("/emails/phishing.eml")) {
            raw = in.readAllBytes();
        }
        scans.scanEmail(team.admin(), "contract-" + java.util.UUID.randomUUID(), raw);

        JsonNode email = json(get("/api/v1/emails")).get(0);
        JsonNode gmail = json(get("/api/v1/integrations/gmail"));

        assertThat(keys(email)).isEqualTo(types.fields("ScannedEmail"));
        assertThat(keys(email.get("links").get(0))).isEqualTo(types.fields("EmailLink"));
        assertThat(keys(email.get("attachments").get(0))).isEqualTo(types.fields("EmailAttachment"));
        assertThat(keys(gmail)).isEqualTo(types.fields("GmailConnection"));
    }

    @Test
    void agentShapesMatch() throws Exception {
        JsonNode registered = json(post("/api/v1/agents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostname\":\"contract-host\"}"));
        JsonNode listed = json(get("/api/v1/agents")).get(0);

        assertThat(keys(registered)).isEqualTo(types.fields("RegisteredAgent"));
        assertThat(keys(listed)).isEqualTo(types.fields("EndpointAgent"));
    }

    @Test
    void reportAndBypassRequestBodiesUseTheDashboardFieldNames() {
        assertThat(types.fields("ReportRequest")).containsExactlyInAnyOrder("indicatorType", "indicatorValue", "threatCategory");
        assertThat(types.fields("BypassRequest")).containsExactlyInAnyOrder("indicatorType", "indicatorValue", "ttlMinutes");
    }
}
