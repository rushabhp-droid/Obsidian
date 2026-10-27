package com.obsidian.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.model.Role;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestInfra;
import com.obsidian.support.WebSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The official demo seed: random per-account passwords that really sign in, and rich, idempotent data. */
@AutoConfigureMockMvc
class DemoDataGeneratorTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired com.obsidian.model.UserRepository users;
    @TempDir Path dir;

    final String domain = "demo-" + UUID.randomUUID().toString().substring(0, 8) + ".example";

    JsonNode fetch(WebSession session, String path) throws Exception {
        return mapper.readTree(mvc.perform(session.cookies(get(path))).andReturn().getResponse().getContentAsString());
    }

    @Test
    void generatedAccountsSignInAndSeeARealisticDemo() throws Exception {
        List<DemoDataGenerator.Account> accounts = DemoDataGenerator.generate(dir, domain, new Random(7), false);
        TestInfra.applySchema(dir.resolve("seed.sql"));
        TestInfra.applySchema(dir.resolve("seed.sql"));

        DemoDataGenerator.Account admin = accounts.stream().filter(a -> a.role() == Role.ADMIN).findFirst().orElseThrow();
        WebSession session = WebSession.signIn(mvc, admin.email(), admin.password());

        assertThat(fetch(session, "/api/v1/auth/me").path("organization").path("name").asText()).isEqualTo("Aster Research");
        JsonNode scans = fetch(session, "/api/v1/scans");
        assertThat(scans.size()).isBetween(100, 200);
        assertThat(scans.findValuesAsText("verdict")).contains("MALICIOUS", "SUSPICIOUS", "PENDING", "CLEAN", "BYPASSED");
        JsonNode emails = fetch(session, "/api/v1/emails");
        assertThat(emails.size()).isGreaterThanOrEqualTo(6);
        assertThat(emails.findValuesAsText("verdict")).contains("MALICIOUS", "PENDING", "CLEAN");
        assertThat(emails.findValues("verifiedOfficial").stream().map(JsonNode::asBoolean)).contains(true, false);
    }

    @Test
    void everyAccountGetsItsOwnStrongPasswordThatNeverReachesTheSqlFile() throws Exception {
        List<DemoDataGenerator.Account> accounts = DemoDataGenerator.generate(dir, domain, new Random(7), false);
        String sql = Files.readString(dir.resolve("seed.sql"));

        assertThat(accounts).extracting(DemoDataGenerator.Account::role).contains(Role.ADMIN, Role.EMPLOYEE, Role.CONSUMER);
        Set<String> passwords = accounts.stream().map(DemoDataGenerator.Account::password).collect(Collectors.toSet());
        assertThat(passwords).hasSize(accounts.size()).allSatisfy(password -> {
            assertThat(password).hasSizeGreaterThanOrEqualTo(23).isNotEqualTo("obsidian-demo-2026");
            assertThat(sql).doesNotContain(password);
        });
        assertThat(sql).contains("$argon2id$v=19$m=19456,t=2,p=1$");
        for (String secret : List.of("CREDENTIALS.md", "credentials.env")) {
            assertThat(Files.getPosixFilePermissions(dir.resolve(secret)))
                    .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            assertThat(Files.readString(dir.resolve(secret))).contains(accounts.get(0).password());
        }
    }

    @Autowired com.obsidian.config.StartupBootstrap bootstrap;

    @Test
    void demoRulesApplyThroughTheStartupSetup() throws Exception {
        DemoDataGenerator.generate(dir, domain, new Random(7), false);
        TestInfra.applySchema(dir.resolve("seed.sql"));

        bootstrap.applyRules(dir.resolve("rules.json"));

        JsonNode rules = mapper.readTree(dir.resolve("rules.json").toFile());
        assertThat(rules.size()).isEqualTo(12);
        assertThat(rules.findValuesAsText("indicatorValue")).doesNotContain("testsafebrowsing.appspot.com");
    }

    @Test
    void loadsOverADatabaseThatAlreadyHasTheseAccountsUnderOtherIds() throws Exception {
        List<DemoDataGenerator.Account> accounts = DemoDataGenerator.generate(dir, domain, new Random(7), false);
        DemoDataGenerator.Account admin = accounts.get(0);
        users.save(new com.obsidian.model.User(admin.email(), Role.CONSUMER)); // e.g. left by the dev seed

        TestInfra.applySchema(dir.resolve("seed.sql"));

        WebSession session = WebSession.signIn(mvc, admin.email(), admin.password());
        assertThat(fetch(session, "/api/v1/auth/me").path("role").asText()).isEqualTo("ADMIN");
        assertThat(fetch(session, "/api/v1/emails").size()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void existingCredentialsAreNotReplacedWithoutForce() throws Exception {
        String first = DemoDataGenerator.generate(dir, domain, new Random(7), false).get(0).password();

        assertThatThrownBy(() -> DemoDataGenerator.generate(dir, domain, new Random(7), false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(DemoDataGenerator.generate(dir, domain, new Random(7), true).get(0).password()).isNotEqualTo(first);
    }
}
