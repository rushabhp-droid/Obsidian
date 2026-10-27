package com.obsidian.codeguard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.auth.Tokens;
import com.obsidian.model.Role;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.UserRepository;
import com.obsidian.scanner.VirusTotalClient;
import com.obsidian.scanner.VtVerdict;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.RedisEvents;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.WebSession;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Signed GitHub webhooks through the real app, against a fake GitHub and stand-in scanners. */
@AutoConfigureMockMvc
class CodeGuardWebhookTest extends IntegrationTest {

    static final String SECRET = "webhook-secret-for-tests";
    static final String ADMIN = "codeguard-admin@aster.example";
    static FakeGitHub github;
    static Path tools;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestAccounts accounts;
    @Autowired UserRepository users;
    @Autowired RedisConnectionFactory redis;
    @Autowired ObjectMapper mapper;
    @MockitoBean VirusTotalClient virusTotal;

    @DynamicPropertySource
    static void codeGuard(DynamicPropertyRegistry registry) throws Exception {
        github = new FakeGitHub();
        tools = Files.createTempDirectory("codeguard-tools");
        Path key = github.writePkcs1Key(tools);
        registry.add("obsidian.codeguard.app-id", () -> "12345");
        registry.add("obsidian.codeguard.private-key-path", key::toString);
        registry.add("obsidian.codeguard.webhook-secret", () -> SECRET);
        registry.add("obsidian.codeguard.org-admin-email", () -> ADMIN);
        registry.add("obsidian.codeguard.api-url", github::url);
        String gitleaks = FakeTools.gitleaks(tools).toString();
        String osv = FakeTools.osvScanner(tools).toString();
        registry.add("obsidian.codeguard.gitleaks", () -> gitleaks);
        registry.add("obsidian.codeguard.osv-scanner", () -> osv);
    }

    @AfterAll
    static void stop() {
        github.close();
    }

    @BeforeEach
    void admin() {
        if (users.findByEmail(ADMIN).isEmpty()) {
            var org = accounts.team(true).org();
            accounts.user(ADMIN, Role.ADMIN, org);
        }
    }

    UUID orgId() {
        return users.findByEmail(ADMIN).orElseThrow().getOrgId();
    }

    static String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    ResultActions deliver(String event, String json, boolean signed) throws Exception {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        var request = post("/api/v1/webhooks/github").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-GitHub-Event", event).header("X-GitHub-Delivery", UUID.randomUUID().toString());
        return mvc.perform(signed ? request.header("X-Hub-Signature-256", sign(body)) : request);
    }

    String push(String repo, String sha) throws Exception {
        github.zipballs.put(sha, FakeGitHub.zip(Map.of(
                "acme-" + sha + "/config/settings.py", "import os\n\nAWS_KEY = \"" + FakeTools.SECRET + "\"\n",
                "acme-" + sha + "/package-lock.json", "{\"lockfileVersion\":3}\n")));
        return """
                {"ref":"refs/heads/main","before":"0000000000000000000000000000000000000000","after":"%s",
                 "deleted":false,"repository":{"full_name":"%s","name":"api"},"installation":{"id":42},
                 "pusher":{"name":"dev"}}""".formatted(sha, repo);
    }

    int findings(String repo) {
        return jdbc.queryForObject("select count(*) from code_findings where repo_full_name = ?", Integer.class, repo);
    }

    @Test
    void unsignedOrForgedWebhooksAreRefusedBeforeParsing() throws Exception {
        String repo = "acme/" + UUID.randomUUID();
        deliver("push", push(repo, "aaaa1111"), false).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/webhooks/github").contentType(MediaType.APPLICATION_JSON).content("{not json")
                        .header("X-GitHub-Event", "push").header("X-Hub-Signature-256", "sha256=" + "0".repeat(64)))
                .andExpect(status().isUnauthorized());

        Thread.sleep(300);
        assertThat(findings(repo)).isZero();
    }

    @Test
    void signedPushIsAcceptedThenScannedWithAFailingCheckRun() throws Exception {
        String repo = "acme/" + UUID.randomUUID();
        String sha = "c0ffee" + UUID.randomUUID().toString().replace("-", "") + "00";
        try (RedisEvents events = new RedisEvents(redis, mapper)) {
            deliver("push", push(repo, sha), true).andExpect(status().isAccepted());

            Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> findings(repo) == 2);
            Map<String, Object> secret = jdbc.queryForMap("select * from code_findings where repo_full_name = ? and "
                    + "finding_type = 'SECRET'", repo);
            assertThat(secret).containsEntry("rule_id", "aws-access-token").containsEntry("location", "config/settings.py:3")
                    .containsEntry("severity", "CRITICAL").containsEntry("trigger", "PUSH").containsEntry("git_ref", sha)
                    .containsEntry("status", "OPEN").containsEntry("org_id", orgId())
                    .containsEntry("secret_fingerprint", Tokens.sha256Hex(FakeTools.SECRET));
            assertThat(jdbc.queryForObject("select count(*) from code_findings where repo_full_name = ? and "
                    + "code_findings::text like ?", Integer.class, repo, "%" + FakeTools.SECRET + "%")).isZero();
            assertThat(jdbc.queryForObject("select severity || ' ' || rule_id || ' ' || location from code_findings "
                    + "where repo_full_name = ? and finding_type = 'VULN_DEPENDENCY'", String.class, repo))
                    .isEqualTo("HIGH GHSA-p6mc-m468-83gw lodash@4.17.15");

            JsonNode run = Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> github.checkRuns.stream()
                    .filter(r -> r.path("head_sha").asText().equals(sha)).findFirst().orElse(null), java.util.Objects::nonNull);
            assertThat(run.path("name").asText()).isEqualTo("Obsidian Code Guard");
            assertThat(run.path("conclusion").asText()).isEqualTo("failure");
            assertThat(run.toString()).doesNotContain(FakeTools.SECRET);
            assertThat(run.path("output").path("annotations").findValuesAsText("path"))
                    .contains("config/settings.py", "package-lock.json");

            JsonNode event = events.await(e -> e.path("kind").asText().equals("CODE")
                    && e.path("event").path("finding").path("repoFullName").asText().equals(repo));
            assertThat(event.path("event").path("type").asText()).isEqualTo("CODE_FINDING");
            assertThat(event.path("orgId").asText()).isEqualTo(orgId().toString());
            assertThat(event.toString()).doesNotContain(FakeTools.SECRET);
        }
        assertThat(Files.list(Path.of(System.getProperty("java.io.tmpdir")))
                .filter(p -> p.getFileName().toString().startsWith("obsidian-codeguard-"))
                .filter(p -> Files.exists(p.resolve("src/config/settings.py")))).isEmpty();
    }

    @Test
    void redeliveredPushAddsNoDuplicates() throws Exception {
        String repo = "acme/" + UUID.randomUUID();
        String payload = push(repo, "beef0001");
        deliver("push", payload, true).andExpect(status().isAccepted());
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> findings(repo) == 2);

        deliver("push", payload, true).andExpect(status().isAccepted());
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> github.checkRuns.stream()
                .filter(r -> r.path("head_sha").asText().equals("beef0001")).count() == 2);

        assertThat(findings(repo)).isEqualTo(2);
    }

    @Test
    void publishedReleaseAssetsAreCheckedAgainstVirusTotal() throws Exception {
        String repo = "acme/" + UUID.randomUUID();
        byte[] trojan = ("MZ trojaned build " + repo).getBytes(StandardCharsets.UTF_8);
        byte[] fresh = ("MZ brand new build " + repo).getBytes(StandardCharsets.UTF_8);
        github.assets.put("901", trojan);
        github.assets.put("902", fresh);
        given(virusTotal.lookupFile(anyString())).willReturn(VtVerdict.pending());
        given(virusTotal.lookupFile(sha256(trojan)))
                .willReturn(VtVerdict.fromStats(14, 0, ThreatCategory.MALWARE, Instant.now()));
        String base = github.url() + "/repos/" + repo + "/releases/assets/";

        deliver("release", """
                {"action":"published","release":{"tag_name":"v2.4.0","assets":[
                  {"id":901,"name":"payments-api-linux-amd64","size":%d,"url":"%s901"},
                  {"id":902,"name":"payments-api-darwin-arm64","size":%d,"url":"%s902"}]},
                 "repository":{"full_name":"%s"},"installation":{"id":42}}"""
                .formatted(trojan.length, base, fresh.length, base, repo), true).andExpect(status().isAccepted());

        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> findings(repo) == 1);
        assertThat(jdbc.queryForMap("select finding_type, severity, rule_id, location, trigger, git_ref from code_findings "
                + "where repo_full_name = ?", repo))
                .containsEntry("finding_type", "MALICIOUS_BINARY").containsEntry("severity", "CRITICAL")
                .containsEntry("rule_id", "VT:14").containsEntry("location", "payments-api-linux-amd64")
                .containsEntry("trigger", "RELEASE").containsEntry("git_ref", "v2.4.0");
    }

    @Test
    void pingAndUnrelatedEventsAreAcknowledged() throws Exception {
        deliver("ping", "{\"zen\":\"Keep it logically awesome.\",\"hook_id\":1}", true).andExpect(status().isAccepted());
        deliver("issues", "{\"action\":\"opened\"}", true).andExpect(status().isAccepted());
    }

    @Test
    void adminsReviewFindingsAndMarkThemRotated() throws Exception {
        String repo = "acme/" + UUID.randomUUID();
        deliver("push", push(repo, "dead0001"), true).andExpect(status().isAccepted());
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> findings(repo) == 2);
        WebSession admin = WebSession.signIn(mvc, ADMIN, TestAccounts.PASSWORD);

        String id = mapper.readTree(mvc.perform(admin.cookies(get("/api/v1/code/findings")
                        .param("repo", repo).param("type", "SECRET")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].secretFingerprint").value(Tokens.sha256Hex(FakeTools.SECRET)))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();

        mvc.perform(admin.withCsrf(post("/api/v1/code/findings/" + id + "/status"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REVOKED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
        assertThat(jdbc.queryForObject("select details ->> 'to' from audit_logs where action = 'CODE_FINDING_STATUS' "
                + "and details ->> 'findingId' = ?", String.class, id)).isEqualTo("REVOKED");
        mvc.perform(admin.withCsrf(post("/api/v1/code/findings/" + id + "/status"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isBadRequest());

        var team = accounts.team(true);
        WebSession employee = WebSession.signIn(mvc, team.employee().email(), TestAccounts.PASSWORD);
        mvc.perform(employee.cookies(get("/api/v1/code/findings"))).andExpect(status().isForbidden());
        WebSession otherAdmin = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);
        mvc.perform(otherAdmin.withCsrf(post("/api/v1/code/findings/" + id + "/status"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FALSE_POSITIVE\"}"))
                .andExpect(status().isNotFound());
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
