package com.obsidian.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Cloudflare is the external system here: only the token "good" passes its siteverify. */
@AutoConfigureMockMvc
class TurnstileEnforcementTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @MockitoBean TurnstileVerifier turnstile;

    @BeforeEach
    void cloudflare() {
        given(turnstile.enabled()).willReturn(true);
        given(turnstile.verify(any())).willReturn(false);
        given(turnstile.verify(eq("good"))).willReturn(true);
    }

    String login(String email, String token) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + TestAccounts.PASSWORD + "\",\"turnstileToken\":"
                + (token == null ? "null" : "\"" + token + "\"") + "}";
    }

    @Test
    void passwordLoginWithoutAValidTokenIs400AndOpensNoSession() throws Exception {
        Team team = accounts.team(true);

        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(team.admin().email(), "bad")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andReturn().getResponse();

        org.assertj.core.api.Assertions.assertThat(response.getHeaders("Set-Cookie"))
                .noneMatch(cookie -> cookie.startsWith("__Host-session="));
    }

    @Test
    void passwordLoginWithAValidTokenSucceeds() throws Exception {
        Team team = accounts.team(true);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(team.admin().email(), "good")))
                .andExpect(status().isOk());
    }

    @Test
    void registerWithoutAValidTokenCreatesNothing() throws Exception {
        String email = "bot-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.example";

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(login(email, null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(login(email, "good")))
                .andExpect(status().isCreated());
    }

    @Test
    void passkeyLoginNeedsTheTurnstileHeader() throws Exception {
        mvc.perform(post("/login/webauthn").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }
}
