package com.obsidian.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.auth.Tokens;
import com.obsidian.model.Channel;
import com.obsidian.model.EndpointAgent;
import com.obsidian.model.EndpointAgentRepository;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ScanLog;
import com.obsidian.model.ScanLogRepository;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.Verdict;
import com.obsidian.rules.RuleService;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import com.obsidian.support.WebSession;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Settings → Organization and Users &amp; roles, the audit log, reports and endpoint detail. */
@AutoConfigureMockMvc
class DashboardManagementTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TestAccounts accounts;
    @Autowired RuleService rules;
    @Autowired ScanLogRepository scanLogs;
    @Autowired EndpointAgentRepository agents;

    Team team;
    WebSession admin;
    WebSession employee;

    @BeforeEach
    void signIn() throws Exception {
        team = accounts.team(true);
        admin = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);
        employee = WebSession.signIn(mvc, team.employee().email(), TestAccounts.PASSWORD);
    }

    ResultActions send(WebSession who, MockHttpServletRequestBuilder request, String body) throws Exception {
        MockHttpServletRequestBuilder withBody = body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(who.withCsrf(withBody));
    }

    JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    static String email(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@aster.example";
    }

    // ---- organization ----------------------------------------------------------------------------

    @Test
    void adminsReadAndChangeTheOrganizationAndEveryChangeIsAudited() throws Exception {
        send(admin, get("/api/v1/org"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(team.org().getName()))
                .andExpect(jsonPath("$.memberCount").value(3))
                .andExpect(jsonPath("$.adminCount").value(1));
        send(admin, put("/api/v1/org"), "{\"name\":\"  Aster   Research \",\"policyAllowUserBypass\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aster Research"))
                .andExpect(jsonPath("$.policyAllowUserBypass").value(false));
        // The policy takes effect for employees at once.
        send(employee, post("/api/v1/rules/bypass"), "{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"x.example\",\"ttlMinutes\":15}")
                .andExpect(status().isForbidden());
        send(admin, put("/api/v1/org"), "{\"name\":\" \"}").andExpect(status().isBadRequest());

        send(employee, get("/api/v1/org"), null).andExpect(status().isForbidden());
        send(employee, put("/api/v1/org"), "{\"name\":\"Mine now\"}").andExpect(status().isForbidden());

        JsonNode audit = json(send(admin, get("/api/v1/audit"), null).andExpect(status().isOk()));
        assertThat(audit.get(0).path("action").asText()).isEqualTo("ORG_UPDATE");
        assertThat(audit.get(0).path("actor").path("email").asText()).isEqualTo(team.admin().email());
        assertThat(audit.get(0).path("details").path("policyAllowUserBypass").path("new").asBoolean()).isFalse();
    }

    // ---- members ---------------------------------------------------------------------------------

    @Test
    void adminsAddEmployeesWhoThenSignInToTheirOwnWorkspace() throws Exception {
        String newcomer = email("jules");
        JsonNode added = json(send(admin, post("/api/v1/org/members"), """
                {"email":"%s","displayName":"Jules Park","role":"EMPLOYEE","temporaryPassword":"a temporary password"}"""
                .formatted(newcomer.toUpperCase())).andExpect(status().isCreated()));
        assertThat(added.path("email").asText()).isEqualTo(newcomer);
        assertThat(added.path("role").asText()).isEqualTo("EMPLOYEE");

        WebSession jules = WebSession.signIn(mvc, newcomer, "a temporary password");
        send(jules, get("/api/v1/auth/me"), null)
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.displayName").value("Jules Park"))
                .andExpect(jsonPath("$.organization.id").value(team.org().getId().toString()));
        send(jules, get("/api/v1/org/members"), null).andExpect(status().isForbidden());

        send(admin, post("/api/v1/org/members"), """
                {"email":"%s","role":"EMPLOYEE","temporaryPassword":"a temporary password"}""".formatted(newcomer))
                .andExpect(status().isConflict());
        send(admin, post("/api/v1/org/members"), """
                {"email":"%s","role":"EMPLOYEE","temporaryPassword":"short"}""".formatted(email("short")))
                .andExpect(status().isBadRequest());
        send(admin, post("/api/v1/org/members"), """
                {"email":"%s","role":"CONSUMER","temporaryPassword":"a temporary password"}""".formatted(email("c")))
                .andExpect(status().isBadRequest());
        send(employee, post("/api/v1/org/members"), """
                {"email":"%s","role":"ADMIN","temporaryPassword":"a temporary password"}""".formatted(email("e")))
                .andExpect(status().isForbidden());

        JsonNode members = json(send(admin, get("/api/v1/org/members"), null).andExpect(status().isOk()));
        assertThat(members.findValuesAsText("email")).contains(team.admin().email(), team.employee().email(), newcomer);
        assertThat(members.get(0).path("you").asBoolean()).isTrue();
    }

    @Test
    void rolesChangeAtOnceAndTheLastAdminStays() throws Exception {
        String employeeId = team.employee().userId().toString();
        String adminId = team.admin().userId().toString();

        send(admin, put("/api/v1/org/members/" + adminId + "/role"), "{\"role\":\"EMPLOYEE\"}").andExpect(status().isConflict());
        send(admin, delete("/api/v1/org/members/" + adminId), null).andExpect(status().isConflict());

        send(admin, put("/api/v1/org/members/" + employeeId + "/role"), "{\"role\":\"ADMIN\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        // The promoted employee's existing session is an admin session now.
        send(employee, get("/api/v1/org/members"), null).andExpect(status().isOk());
        send(admin, put("/api/v1/org/members/" + adminId + "/role"), "{\"role\":\"EMPLOYEE\"}").andExpect(status().isOk());
        send(admin, get("/api/v1/org/members"), null).andExpect(status().isForbidden());

        Team other = accounts.team(true);
        send(employee, put("/api/v1/org/members/" + other.employee().userId() + "/role"), "{\"role\":\"ADMIN\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void removedMembersLoseAccessImmediately() throws Exception {
        String colleagueId = team.colleague().userId().toString();
        WebSession colleague = WebSession.signIn(mvc, team.colleague().email(), TestAccounts.PASSWORD);
        rules.report(team.colleague(), IndicatorType.DOMAIN, "colleague-" + UUID.randomUUID().toString().substring(0, 8)
                + ".example", ThreatCategory.PHISHING);

        send(admin, delete("/api/v1/org/members/" + colleagueId), null).andExpect(status().isNoContent());

        send(colleague, get("/api/v1/auth/me"), null).andExpect(status().isUnauthorized());
        send(admin, delete("/api/v1/org/members/" + colleagueId), null).andExpect(status().isNotFound());
        JsonNode audit = json(send(admin, get("/api/v1/audit"), null));
        assertThat(audit.get(0).path("action").asText()).isEqualTo("MEMBER_REMOVE");
        assertThat(audit.get(0).path("details").path("memberEmail").asText()).isEqualTo(team.colleague().email());
        // The colleague's earlier report stays in the trail, without an actor now.
        assertThat(audit.findValuesAsText("action")).contains("REPORT");
    }

    // ---- audit log -------------------------------------------------------------------------------

    @Test
    void auditRecordsShowTheRuleAndItsHistoryButNeverOtherOrganizations() throws Exception {
        String domain = "audit-" + UUID.randomUUID().toString().substring(0, 8) + ".example";
        rules.report(team.employee(), IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING);
        rules.bypass(team.admin(), IndicatorType.DOMAIN, domain, null);

        JsonNode audit = json(send(admin, get("/api/v1/audit?limit=2"), null).andExpect(status().isOk()));
        assertThat(audit.size()).isEqualTo(2);
        String bypassId = audit.get(0).path("id").asText();
        assertThat(audit.get(0).path("action").asText()).isEqualTo("BYPASS");

        send(admin, get("/api/v1/audit/" + bypassId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.action").value("BYPASS"))
                .andExpect(jsonPath("$.rule.indicatorValue").value(domain))
                .andExpect(jsonPath("$.rule.listType").value("WHITELIST"))
                .andExpect(jsonPath("$.ruleHistory.length()").value(2))
                .andExpect(jsonPath("$.ruleHistory[0].action").value("REPORT"))
                .andExpect(jsonPath("$.ruleHistory[0].actor.email").value(team.employee().email()));

        send(employee, get("/api/v1/audit"), null).andExpect(status().isForbidden());
        Team other = accounts.team(true);
        WebSession otherAdmin = WebSession.signIn(mvc, other.admin().email(), TestAccounts.PASSWORD);
        send(otherAdmin, get("/api/v1/audit/" + bypassId), null).andExpect(status().isNotFound());
        assertThat(json(send(otherAdmin, get("/api/v1/audit"), null)).findValuesAsText("id")).doesNotContain(bypassId);
    }

    // ---- reports ---------------------------------------------------------------------------------

    @Test
    void reportsCountTheOrganizationForAdminsAndOwnScansForEmployees() throws Exception {
        scanLogs.save(new ScanLog(team.employee().userId(), Channel.SMS, "+15550100", "http://a.example", Verdict.MALICIOUS, 9));
        scanLogs.save(new ScanLog(team.employee().userId(), Channel.EMAIL, "x@y.example", "http://b.example", Verdict.PENDING, 0));
        scanLogs.save(new ScanLog(team.admin().userId(), Channel.WHATSAPP, "+15550101", "http://c.example", Verdict.SUSPICIOUS, 1));
        Team other = accounts.team(true);
        scanLogs.save(new ScanLog(other.employee().userId(), Channel.SMS, "+15550102", "http://d.example", Verdict.MALICIOUS, 5));

        JsonNode org = json(send(admin, get("/api/v1/reports/summary?days=7"), null).andExpect(status().isOk()));
        assertThat(org.path("scope").asText()).isEqualTo("ORGANIZATION");
        assertThat(org.path("totals").path("total").asLong()).isEqualTo(3);
        assertThat(org.path("totals").path("malicious").asLong()).isEqualTo(1);
        assertThat(org.path("byDay").size()).isEqualTo(7);
        assertThat(org.path("byDay").get(6).path("counts").path("total").asLong()).isEqualTo(3);
        assertThat(org.path("bySource").findValuesAsText("channel")).containsExactlyInAnyOrder("EMAIL", "SMS", "WHATSAPP");

        JsonNode mine = json(send(employee, get("/api/v1/reports/summary?days=1"), null).andExpect(status().isOk()));
        assertThat(mine.path("scope").asText()).isEqualTo("USER");
        assertThat(mine.path("totals").path("total").asLong()).isEqualTo(2);
        assertThat(mine.path("totals").path("pending").asLong()).isEqualTo(1);
    }

    // ---- endpoint detail -------------------------------------------------------------------------

    @Test
    void endpointDetailShowsWhatTheDaemonEnforces() throws Exception {
        EndpointAgent agent = agents.save(new EndpointAgent(team.org().getId(), "build-linux-01", Tokens.sha256Hex(Tokens.generate("obs_"))));
        String domain = "blocked-" + UUID.randomUUID().toString().substring(0, 8) + ".example";
        rules.report(team.admin(), IndicatorType.DOMAIN, domain, ThreatCategory.MALWARE);

        send(admin, get("/api/v1/agents/" + agent.getId()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent.hostname").value("build-linux-01"))
                .andExpect(jsonPath("$.agent.token").doesNotExist())
                .andExpect(jsonPath("$.enforced[?(@.value == '" + domain + "')]").exists());
        send(employee, get("/api/v1/agents/" + agent.getId()), null).andExpect(status().isForbidden());
        Team other = accounts.team(true);
        WebSession otherAdmin = WebSession.signIn(mvc, other.admin().email(), TestAccounts.PASSWORD);
        send(otherAdmin, get("/api/v1/agents/" + agent.getId()), null).andExpect(status().isNotFound());
    }
}
