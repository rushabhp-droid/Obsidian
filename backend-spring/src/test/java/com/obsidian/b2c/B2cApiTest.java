package com.obsidian.b2c;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.b2c.CodeMailer.Purpose;
import com.obsidian.b2c.GoogleTokenVerifier.GoogleIdentity;
import com.obsidian.model.Account;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ThreatCategory;
import com.obsidian.rules.FirewallRule;
import com.obsidian.rules.RuleService;
import com.obsidian.scanner.VirusTotalClient;
import com.obsidian.scanner.VtVerdict;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import com.obsidian.support.WebSession;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The B2C app API end to end: sign-up and sign-in, separation from /api/v1, scans, rules and reports. */
@AutoConfigureMockMvc
@Import(B2cApiTest.Mail.class)
class B2cApiTest extends IntegrationTest {

    static final String PASSWORD = "a long enough password";

    /** Captures codes instead of sending email. */
    static class RecordingMailer extends CodeMailer {
        final Map<String, String> codes = new ConcurrentHashMap<>();

        RecordingMailer(B2cProperties properties) {
            super(properties);
        }

        @Override
        public void send(String email, String name, String code, Purpose purpose) {
            codes.put(purpose + " " + email, code);
        }
    }

    @TestConfiguration
    static class Mail {
        @Bean
        @Primary
        RecordingMailer recordingMailer(B2cProperties properties) {
            return new RecordingMailer(properties);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RecordingMailer mailer;
    @Autowired TestAccounts accounts;
    @Autowired RuleService ruleService;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsumerReportService reportService;
    @MockitoBean VirusTotalClient virusTotal;
    @MockitoBean GoogleTokenVerifier google;

    @BeforeEach
    void unknownToVirusTotal() {
        given(virusTotal.lookupUrl(anyString())).willReturn(VtVerdict.pending());
        given(virusTotal.lookupFile(anyString())).willReturn(VtVerdict.pending());
        given(virusTotal.lookupDomain(anyString())).willReturn(VtVerdict.pending());
        given(virusTotal.lookupIp(anyString())).willReturn(VtVerdict.pending());
    }

    /** Global rules apply to every organization, so other tests' daemon SYNCs would see these. */
    @AfterEach
    void removeGlobalRules() {
        jdbc.update("delete from threat_rules where org_id is null and user_id is null");
    }

    static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    ResultActions postJson(String path, Object body, String token) throws Exception {
        MockHttpServletRequestBuilder request = post(path).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    ResultActions getWith(String path, String token) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Registers and verifies a consumer; returns its bearer token. */
    String signUp(String email) throws Exception {
        postJson("/api/b2c/v1/auth/register", Map.of("name", "Sam Ortiz", "email", email, "password", PASSWORD), null)
                .andExpect(status().isAccepted());
        String code = mailer.codes.get(Purpose.VERIFY_EMAIL + " " + email.toLowerCase());
        return json(postJson("/api/b2c/v1/auth/verify-email", Map.of("email", email, "code", code), null)
                .andExpect(status().isOk())).get("token").asText();
    }

    String consumer() throws Exception {
        return signUp(unique("sam") + "@mail.example");
    }

    // ---- sign-up and sign-in ---------------------------------------------------------------------------

    @Test
    void signUpVerifiesTheEmailWithACodeThenSignsIn() throws Exception {
        String email = unique("Sam") + "@Mail.example";
        postJson("/api/b2c/v1/auth/register", Map.of("name", "  Sam   Ortiz ", "email", email, "password", PASSWORD), null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.expiresInSeconds").value(600));

        postJson("/api/b2c/v1/auth/login", Map.of("email", email, "password", PASSWORD), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        postJson("/api/b2c/v1/auth/verify-email", Map.of("email", email, "code", "000000x"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));

        String code = mailer.codes.get(Purpose.VERIFY_EMAIL + " " + email.toLowerCase());
        String token = json(postJson("/api/b2c/v1/auth/verify-email", Map.of("email", email, "code", code), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.startsWith("obc_")))
                .andExpect(jsonPath("$.consumer.displayName").value("Sam Ortiz"))
                .andExpect(jsonPath("$.consumer.emailVerified").value(true))).get("token").asText();

        getWith("/api/b2c/v1/me", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.hasPassword").value(true));

        // The code is single use.
        postJson("/api/b2c/v1/auth/verify-email", Map.of("email", email, "code", code), null)
                .andExpect(status().isBadRequest());
        postJson("/api/b2c/v1/auth/login", Map.of("email", email, "password", PASSWORD), null)
                .andExpect(status().isOk());
        postJson("/api/b2c/v1/auth/login", Map.of("email", email, "password", "wrong password!"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        postJson("/api/b2c/v1/auth/register", Map.of("name", "Someone", "email", email, "password", PASSWORD), null)
                .andExpect(status().isConflict());
    }

    @Test
    void nameIsRequired() throws Exception {
        postJson("/api/b2c/v1/auth/register", Map.of("name", "  ", "email", unique("x") + "@mail.example",
                "password", PASSWORD), null).andExpect(status().isBadRequest());
    }

    @Test
    void forgottenPasswordIsResetWithAnEmailedCode() throws Exception {
        String email = unique("reset") + "@mail.example";
        signUp(email);
        postJson("/api/b2c/v1/auth/forgot-password", Map.of("email", email), null).andExpect(status().isAccepted());
        postJson("/api/b2c/v1/auth/forgot-password", Map.of("email", unique("nobody") + "@mail.example"), null)
                .andExpect(status().isAccepted());

        String code = mailer.codes.get(Purpose.RESET_PASSWORD + " " + email);
        postJson("/api/b2c/v1/auth/reset-password", Map.of("email", email, "code", code,
                "newPassword", "a brand new password"), null).andExpect(status().isOk());
        postJson("/api/b2c/v1/auth/login", Map.of("email", email, "password", "a brand new password"), null)
                .andExpect(status().isOk());
        postJson("/api/b2c/v1/auth/login", Map.of("email", email, "password", PASSWORD), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleSignInCreatesTheAccountAndDropsAnUnprovenPassword() throws Exception {
        String email = unique("gina") + "@gmail.example";
        // Someone else registered this email but never verified it.
        postJson("/api/b2c/v1/auth/register", Map.of("name", "Impostor", "email", email, "password", PASSWORD), null)
                .andExpect(status().isAccepted());
        given(google.verify("google-id-token")).willReturn(new GoogleIdentity(unique("sub"), email, "Gina Park"));

        postJson("/api/b2c/v1/auth/google", Map.of("idToken", "google-id-token"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumer.emailVerified").value(true))
                .andExpect(jsonPath("$.consumer.googleLinked").value(true))
                .andExpect(jsonPath("$.consumer.hasPassword").value(false));
        postJson("/api/b2c/v1/auth/login", Map.of("email", email, "password", PASSWORD), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consumerAndDashboardTokensOnlyWorkOnTheirOwnApi() throws Exception {
        String consumerToken = consumer();
        Team team = accounts.team(true);
        String dashboardToken = json(postJson("/api/v1/auth/login", Map.of("email", team.employee().email(),
                "password", TestAccounts.PASSWORD, "client", "MOBILE"), null)).get("token").asText();

        getWith("/api/v1/rules", consumerToken).andExpect(status().isUnauthorized());
        getWith("/api/b2c/v1/me", dashboardToken).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/b2c/v1/me")).andExpect(status().isUnauthorized());
        WebSession session = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);
        mvc.perform(session.cookies(get("/api/b2c/v1/activity"))).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/b2c/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.cache").value("UP"))
                .andExpect(jsonPath("$.googleClientId").value("test-google-client-id"));

        postJson("/api/b2c/v1/auth/logout", Map.of(), consumerToken).andExpect(status().isNoContent());
        getWith("/api/b2c/v1/me", consumerToken).andExpect(status().isUnauthorized());
    }

    // ---- scanning --------------------------------------------------------------------------------------

    @Test
    void personalRulesThenSharedRulesThenVirusTotalDecideALink() throws Exception {
        String token = consumer();
        String blocked = unique("blocked") + ".example";
        String shared = unique("shared") + ".example";
        postJson("/api/b2c/v1/rules", Map.of("indicatorType", "DOMAIN", "indicatorValue", blocked, "listType", "BLACKLIST"),
                token).andExpect(status().isOk());
        ruleService.blockGlobally(null, IndicatorType.DOMAIN, shared, ThreatCategory.PHISHING,
                com.obsidian.model.RuleSource.ADMIN_POLICY, null);

        // A subdomain matches its parent's rule.
        postJson("/api/b2c/v1/scan/url", Map.of("url", "https://login." + blocked + "/x"), token)
                .andExpect(jsonPath("$.verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.decidedBy").value("PERSONAL_RULE"))
                .andExpect(jsonPath("$.riskScore").value(95));
        postJson("/api/b2c/v1/scan/url", Map.of("url", "http://" + shared + "/reset", "source", "LINK_OPENED"), token)
                .andExpect(jsonPath("$.verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.category").value("PHISHING"))
                .andExpect(jsonPath("$.decidedBy").value("SHARED_RULE"));

        // A personal whitelist beats the shared blocklist.
        postJson("/api/b2c/v1/rules", Map.of("indicatorType", "DOMAIN", "indicatorValue", shared, "listType", "WHITELIST"),
                token).andExpect(status().isOk());
        postJson("/api/b2c/v1/scan/url", Map.of("url", "http://" + shared + "/reset"), token)
                .andExpect(jsonPath("$.verdict").value("CLEAN"))
                .andExpect(jsonPath("$.trusted").value(true));

        // Nothing known: PENDING, never CLEAN.
        postJson("/api/b2c/v1/scan/url", Map.of("url", "https://" + unique("new") + ".example"), token)
                .andExpect(jsonPath("$.verdict").value("PENDING"))
                .andExpect(jsonPath("$.riskScore").isEmpty());

        String flagged = "https://" + unique("vt") + ".example/login";
        given(virusTotal.lookupUrl(flagged)).willReturn(VtVerdict.fromStats(8, 0, ThreatCategory.PHISHING, Instant.now()));
        postJson("/api/b2c/v1/scan/url", Map.of("url", flagged), token)
                .andExpect(jsonPath("$.verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.decidedBy").value("VIRUSTOTAL"))
                .andExpect(jsonPath("$.vtDetections").value(8))
                .andExpect(jsonPath("$.riskScore").value(94));

        getWith("/api/b2c/v1/activity", token)
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].kind").value("URL_SCAN"))
                .andExpect(jsonPath("$[2].verdict").value("BYPASSED"))
                .andExpect(jsonPath("$[3].kind").value("LINK_OPENED"));
    }

    @Test
    void filesAreCheckedByHashAndMessagesFromSavedContactsCarryNoText() throws Exception {
        String token = consumer();
        String hash = "a".repeat(64);
        given(virusTotal.lookupFile(hash)).willReturn(VtVerdict.fromStats(30, 0, ThreatCategory.MALWARE, Instant.now()));
        postJson("/api/b2c/v1/scan/file", Map.of("sha256", hash.toUpperCase(), "fileName", "invoice.apk"), token)
                .andExpect(jsonPath("$.verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.category").value("MALWARE"));

        postJson("/api/b2c/v1/scan/message", Map.of("app", "com.whatsapp", "sender", "Mum", "savedContact", true,
                "text", "hi"), token).andExpect(status().isBadRequest());
        postJson("/api/b2c/v1/scan/message", Map.of("app", "com.google.android.apps.messaging", "sender", "+1 555 0100",
                "savedContact", false, "text", "Your parcel is held: www." + unique("parcel") + ".example/pay"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("PENDING"))
                .andExpect(jsonPath("$.indicators[0].indicatorType").value("URL"));

        getWith("/api/b2c/v1/activity", token)
                .andExpect(jsonPath("$[0].kind").value("MESSAGE"))
                .andExpect(jsonPath("$[0].subject").value("+1 555 0100"))
                .andExpect(jsonPath("$[0].detail").value("com.google.android.apps.messaging"))
                .andExpect(jsonPath("$[1].subject").value("invoice.apk"));
    }

    @Test
    void rulesArePersonal() throws Exception {
        String sam = consumer();
        String other = consumer();
        String id = json(postJson("/api/b2c/v1/rules", Map.of("indicatorType", "DOMAIN",
                "indicatorValue", " Mine." + unique("x") + ".EXAMPLE ", "listType", "BLACKLIST"), sam)).get("id").asText();

        getWith("/api/b2c/v1/rules", other).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/b2c/v1/rules/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        getWith("/api/b2c/v1/firewall/blocklist", sam).andExpect(jsonPath("$.personal.length()").value(1));
        mvc.perform(delete("/api/b2c/v1/rules/" + id).header("Authorization", "Bearer " + sam))
                .andExpect(status().isNoContent());
        getWith("/api/b2c/v1/rules", sam).andExpect(jsonPath("$.length()").value(0));
        postJson("/api/b2c/v1/rules", Map.of("indicatorType", "DOMAIN", "indicatorValue", "http://x.example/a",
                "listType", "BLACKLIST"), sam).andExpect(status().isBadRequest());
    }

    // ---- reports ---------------------------------------------------------------------------------------

    Map<String, Object> domainReport(String domain) {
        return Map.of("indicatorType", "DOMAIN", "indicatorValue", domain, "threatCategory", "PHISHING");
    }

    @Test
    void aReportVirusTotalFlagsIsBlockedEverywhereAtOnce() throws Exception {
        String token = consumer();
        String domain = unique("flagged") + ".example";
        given(virusTotal.lookupDomain(domain)).willReturn(VtVerdict.fromStats(6, 0, ThreatCategory.MALICIOUS, Instant.now()));

        postJson("/api/b2c/v1/reports", domainReport(domain), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        Team team = accounts.team(true);
        assertThat(ruleService.firewallBlocks(team.org().getId())).extracting(FirewallRule::value).contains(domain);
        getWith("/api/b2c/v1/firewall/blocklist", consumer()).andExpect(jsonPath("$.domains").value(
                org.hamcrest.Matchers.hasItem(domain)));
        Map<String, Object> audit = jdbc.queryForMap("select a.action, a.actor_user_id, a.details::text as details"
                + " from audit_logs a join threat_rules r on r.id = a.rule_id where r.indicator_value = ?", domain);
        assertThat(audit.get("action")).isEqualTo("CONSUMER_REPORT");
        assertThat(audit.get("actor_user_id")).isNull();
        assertThat(audit.get("details").toString()).contains("\"promotedBy\": \"VIRUSTOTAL\"", "\"scope\": \"GLOBAL\"");
    }

    @Test
    void theRecheckJobBlocksAWaitingReportOnceVirusTotalFlagsIt() throws Exception {
        String token = consumer();
        String domain = unique("late-verdict") + ".example";
        postJson("/api/b2c/v1/reports", domainReport(domain), token).andExpect(jsonPath("$.status").value("WAITING"));

        given(virusTotal.lookupDomain(domain)).willReturn(VtVerdict.fromStats(9, 0, ThreatCategory.MALICIOUS, Instant.now()));
        reportService.recheckWaiting();

        getWith("/api/b2c/v1/reports", token).andExpect(jsonPath("$[0].status").value("BLOCKED"));
        assertThat(jdbc.queryForObject("select source from threat_rules where indicator_value = ? and org_id is null",
                String.class, domain)).isEqualTo("VIRUSTOTAL");
    }

    @Test
    void anUnflaggedReportWaitsForThreeVerifiedReporters() throws Exception {
        String domain = unique("crowd") + ".example";
        String first = consumer();
        postJson("/api/b2c/v1/reports", domainReport(domain), first)
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.reporters").value(1))
                .andExpect(jsonPath("$.reportersNeeded").value(3));
        // The reporter is protected right away by a personal rule.
        postJson("/api/b2c/v1/scan/url", Map.of("url", "https://" + domain), first)
                .andExpect(jsonPath("$.verdict").value("MALICIOUS"));
        // Reporting twice doesn't count twice.
        postJson("/api/b2c/v1/reports", domainReport(domain), first).andExpect(jsonPath("$.reporters").value(1));

        postJson("/api/b2c/v1/reports", domainReport(domain), consumer()).andExpect(jsonPath("$.status").value("WAITING"));
        postJson("/api/b2c/v1/reports", domainReport(domain), consumer()).andExpect(jsonPath("$.status").value("BLOCKED"));

        getWith("/api/b2c/v1/reports", first).andExpect(jsonPath("$[0].status").value("BLOCKED"));
    }

    @Test
    void protectedDomainsNeverBlockGloballyAndAdminsApproveTheRest() throws Exception {
        String token = consumer();
        postJson("/api/b2c/v1/reports", domainReport("Accounts.Google.com"), token)
                .andExpect(jsonPath("$.status").value("PROTECTED"));
        postJson("/api/b2c/v1/reports", domainReport("someone." + unique("x") + ".github.io"), token)
                .andExpect(jsonPath("$.status").value("WAITING"));
        String waiting = unique("approve") + ".example";
        postJson("/api/b2c/v1/reports", Map.of("indicatorType", "DOMAIN", "indicatorValue", waiting,
                "note", "Fake bank text"), token).andExpect(jsonPath("$.status").value("WAITING"));

        Team team = accounts.team(true);
        WebSession admin = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);
        WebSession employee = WebSession.signIn(mvc, team.employee().email(), TestAccounts.PASSWORD);

        mvc.perform(employee.cookies(get("/api/v1/consumer-reports"))).andExpect(status().isForbidden());
        JsonNode queue = json(mvc.perform(admin.cookies(get("/api/v1/consumer-reports"))).andExpect(status().isOk()));
        assertThat(queue.findValuesAsText("indicatorValue")).contains("accounts.google.com", waiting);

        mvc.perform(admin.withCsrf(post("/api/v1/consumer-reports/approve")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"accounts.google.com\"}"))
                .andExpect(status().isConflict());
        mvc.perform(admin.withCsrf(post("/api/v1/consumer-reports/approve")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"indicatorType\":\"DOMAIN\",\"indicatorValue\":\"" + waiting + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listType").value("BLACKLIST"))
                .andExpect(jsonPath("$.orgId").isEmpty());
        assertThat(ruleService.firewallBlocks(team.org().getId())).extracting(FirewallRule::value).contains(waiting)
                .doesNotContain("accounts.google.com");
        Account approver = team.admin();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'CONSUMER_REPORT'"
                + " and actor_user_id = ?", Integer.class, approver.userId())).isEqualTo(1);

        // Admin-added protected domains.
        String company = unique("aster") + ".example";
        mvc.perform(admin.withCsrf(post("/api/v1/protected-domains")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"" + company + "\"}")).andExpect(status().isOk());
        postJson("/api/b2c/v1/reports", domainReport("mail." + company), token)
                .andExpect(jsonPath("$.status").value("PROTECTED"));
    }
}
