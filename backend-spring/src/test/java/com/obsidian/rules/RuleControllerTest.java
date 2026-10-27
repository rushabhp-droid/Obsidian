package com.obsidian.rules;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import com.obsidian.support.WebSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class RuleControllerTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper mapper;

    Team team;
    WebSession admin;
    WebSession employee;

    @BeforeEach
    void signIn() throws Exception {
        team = accounts.team(true);
        admin = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);
        employee = WebSession.signIn(mvc, team.employee().email(), TestAccounts.PASSWORD);
    }

    ResultActions report(WebSession who, String body) throws Exception {
        return mvc.perform(who.withCsrf(post("/api/v1/rules/report")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions bypass(WebSession who, String body) throws Exception {
        return mvc.perform(who.withCsrf(post("/api/v1/rules/bypass")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void employeeReportReturnsTheOrgRule() throws Exception {
        report(employee, "{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\" Login-Verify.EXAMPLE \",\"threatCategory\":\"PHISHING\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicatorValue").value("login-verify.example"))
                .andExpect(jsonPath("$.listType").value("BLACKLIST"))
                .andExpect(jsonPath("$.threatCategory").value("PHISHING"))
                .andExpect(jsonPath("$.source").value("USER_REPORT"))
                .andExpect(jsonPath("$.orgId").value(team.org().getId().toString()))
                .andExpect(jsonPath("$.userId").isEmpty())
                .andExpect(jsonPath("$.confidenceScore").value(100));
    }

    @Test
    void listShowsTheOrgRulesNewestFirst() throws Exception {
        report(admin, "{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"first.example\"}").andExpect(status().isOk());
        report(employee, "{\"indicatorType\":\"IP\",\"indicatorValue\":\"203.0.113.45\"}").andExpect(status().isOk());

        mvc.perform(employee.cookies(get("/api/v1/rules")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].indicatorValue").value("203.0.113.45"))
                .andExpect(jsonPath("$[1].indicatorValue").value("first.example"))
                .andExpect(jsonPath("$[1].source").value("ADMIN_POLICY"));
    }

    @Test
    void invalidIndicatorIsAProblemDetail() throws Exception {
        report(admin, "{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"http://evil.example/path\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Enter a domain such as login-verify.example, without a scheme or path."));
    }

    @Test
    void reportCannotBeCategorizedSafe() throws Exception {
        report(admin, "{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"x.example\",\"threatCategory\":\"SAFE\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownIndicatorTypeIs400() throws Exception {
        report(admin, "{\"indicatorType\":\"BOGUS\",\"indicatorValue\":\"x.example\"}").andExpect(status().isBadRequest());
    }

    @Test
    void missingValueIs400() throws Exception {
        report(admin, "{\"indicatorType\":\"DOMAIN\"}").andExpect(status().isBadRequest());
    }

    @Test
    void employeeBypassWithoutTtlIs400() throws Exception {
        bypass(employee, "{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"x.example\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bypasses in an organization need ttlMinutes."));
    }

    @Test
    void employeeBypassWithTtlIsPersonal() throws Exception {
        bypass(employee, "{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"x.example\",\"ttlMinutes\":15}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listType").value("WHITELIST"))
                .andExpect(jsonPath("$.userId").value(team.employee().userId().toString()))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void bypassForbiddenByOrgPolicyIs403() throws Exception {
        Team strict = accounts.team(false);
        WebSession strictEmployee = WebSession.signIn(mvc, strict.employee().email(), TestAccounts.PASSWORD);

        bypass(strictEmployee, "{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"x.example\",\"ttlMinutes\":15}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void employeeCannotOverrideAnAdminPolicy() throws Exception {
        bypass(admin, "{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"docs.aster.example\"}").andExpect(status().isOk());

        report(employee, "{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"docs.aster.example\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void rulesNeedASession() throws Exception {
        mvc.perform(get("/api/v1/rules")).andExpect(status().isUnauthorized());
    }

    @Test
    void mutationsNeedTheCsrfHeader() throws Exception {
        mvc.perform(admin.cookies(post("/api/v1/rules/report")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"x.example\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mobileBearerReportsWithoutCsrf() throws Exception {
        String token = mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + team.employee().email() + "\",\"password\":\"" + TestAccounts.PASSWORD
                                + "\",\"client\":\"MOBILE\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(post("/api/v1/rules/report").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"from-phone.example\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value(team.org().getId().toString()));
    }
}
