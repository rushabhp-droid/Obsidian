package com.obsidian.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.model.Role;
import com.obsidian.model.User;
import com.obsidian.model.UserRepository;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import com.obsidian.support.WebSession;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    static String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"turnstileToken\":null}";
    }

    static String fresh(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@aster.example";
    }

    @Test
    void loginSetsAHostOnlySessionCookie() throws Exception {
        Team team = accounts.team(true);

        MockHttpServletResponse response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(json(team.admin().email(), TestAccounts.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String cookie = response.getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith("__Host-session=")).findFirst().orElseThrow();
        assertThat(cookie).contains("Path=/").contains("Secure").contains("HttpOnly").contains("SameSite=Lax")
                .doesNotContainIgnoringCase("Domain=");
    }

    @Test
    void sessionCookieSignsInMeWithTheDashboardShape() throws Exception {
        Team team = accounts.team(false);
        WebSession browser = WebSession.signIn(mvc, team.employee().email(), TestAccounts.PASSWORD);

        String body = mvc.perform(browser.cookies(get("/api/v1/auth/me")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode me = mapper.readTree(body);
        assertThat(fieldNames(me)).containsExactlyInAnyOrder("email", "displayName", "role", "organization");
        assertThat(fieldNames(me.get("organization"))).containsExactlyInAnyOrder("id", "name", "policyAllowUserBypass");
        assertThat(me.get("email").asText()).isEqualTo(team.employee().email());
        assertThat(me.get("role").asText()).isEqualTo("EMPLOYEE");
        assertThat(me.get("organization").get("id").asText()).isEqualTo(team.org().getId().toString());
        assertThat(me.get("organization").get("policyAllowUserBypass").asBoolean()).isFalse();
        assertThat(me.get("displayName").asText()).isNotBlank();
    }

    @Test
    void loginIgnoresEmailCase() throws Exception {
        Team team = accounts.team(true);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(team.admin().email().toUpperCase(), TestAccounts.PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        Team team = accounts.team(true);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(team.admin().email(), "not the password at all")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(fresh("nobody"), TestAccounts.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passkeyOnlyAccountCannotUsePasswordLogin() throws Exception {
        String email = fresh("passkey-only");
        users.save(new User(email, Role.CONSUMER));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, TestAccounts.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutASessionIs401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutNeedsTheCsrfTokenAndEndsTheSession() throws Exception {
        Team team = accounts.team(true);
        WebSession browser = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);

        mvc.perform(browser.cookies(post("/api/v1/auth/logout"))).andExpect(status().isForbidden());
        mvc.perform(browser.withCsrf(post("/api/v1/auth/logout"))).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").cookie(browser.sessionCookie())).andExpect(status().isUnauthorized());
    }

    @Test
    void mobileLoginReturnsABearerTokenAndNoSession() throws Exception {
        Team team = accounts.team(true);

        MockHttpServletResponse response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + team.employee().email() + "\",\"password\":\"" + TestAccounts.PASSWORD
                                + "\",\"turnstileToken\":null,\"client\":\"MOBILE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        JsonNode body = mapper.readTree(response.getContentAsString());
        String token = body.get("token").asText();
        assertThat(token).matches("obm_[0-9a-f]{64}");
        assertThat(body.get("expiresAt").asText()).isNotBlank();
        assertThat(response.getHeaders("Set-Cookie")).noneMatch(header -> header.startsWith("__Host-session="));

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(team.employee().email()));
    }

    @Test
    void unknownBearerTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer obm_" + "0".repeat(64)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mobileLogoutRevokesTheToken() throws Exception {
        Team team = accounts.team(true);
        String token = mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + team.employee().email() + "\",\"password\":\"" + TestAccounts.PASSWORD
                                + "\",\"client\":\"MOBILE\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerCreatesASignedInConsumerWithAnArgon2idHash() throws Exception {
        String email = fresh("new-consumer");
        WebSession browser = new WebSession();

        MockHttpServletResponse response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + TestAccounts.PASSWORD
                                + "\",\"displayName\":\"Riley Chen\",\"turnstileToken\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Riley Chen"))
                .andExpect(jsonPath("$.role").value("CONSUMER"))
                .andExpect(jsonPath("$.organization").isEmpty())
                .andReturn().getResponse();
        browser.remember(response);

        assertThat(jdbc.queryForObject("select password_hash from users where email = ?", String.class, email))
                .startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        mvc.perform(browser.cookies(get("/api/v1/auth/me"))).andExpect(status().isOk());
    }

    @Test
    void registerWithAnOrganizationNameCreatesItsAdmin() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + fresh("founder") + "\",\"password\":\"" + TestAccounts.PASSWORD
                                + "\",\"organizationName\":\"Northwind Labs\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.organization.name").value("Northwind Labs"))
                .andExpect(jsonPath("$.organization.policyAllowUserBypass").value(true));
    }

    @Test
    void registerRejectsAnExistingEmail() throws Exception {
        Team team = accounts.team(true);

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(team.admin().email(), TestAccounts.PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void registerRejectsAShortPasswordWithAProblemDetail() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(fresh("short"), "too-short")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void springDefaultLoginAndLogoutPagesAreOff() throws Exception {
        for (String page : List.of("/login", "/logout")) {
            MockHttpServletResponse response = mvc.perform(get(page)).andReturn().getResponse();
            assertThat(response.getStatus()).as(page).isEqualTo(401);
            assertThat(java.util.Objects.toString(response.getContentType(), "")).as(page).doesNotContain("text/html");
        }
    }

    @Test
    void springDefaultPasskeyRegistrationPageIsOff() throws Exception {
        Team team = accounts.team(true);
        WebSession browser = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);

        MockHttpServletResponse response = mvc.perform(browser.cookies(get("/webauthn/register")))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(java.util.Objects.toString(response.getContentType(), "")).doesNotContain("text/html");
    }

    @Test
    void passkeyAuthenticationOptionsNeedNoSessionOrCsrf() throws Exception {
        mvc.perform(post("/webauthn/authenticate/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").isNotEmpty())
                .andExpect(jsonPath("$.rpId").value("localhost"));
    }

    @Test
    void passkeyRegistrationOptionsSurviveTheRedisSession() throws Exception {
        Team team = accounts.team(true);
        WebSession browser = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);

        mvc.perform(browser.withCsrf(post("/webauthn/register/options")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").isNotEmpty())
                .andExpect(jsonPath("$.rp.id").value("localhost"))
                .andExpect(jsonPath("$.user.name").value(team.admin().email()));

        mvc.perform(browser.cookies(get("/api/v1/auth/me"))).andExpect(status().isOk());
    }

    static List<String> fieldNames(JsonNode node) {
        List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
