package com.obsidian.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import com.obsidian.support.WebSession;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/** Google is the external system: GmailClient is stubbed, the token store and sessions are real. */
@AutoConfigureMockMvc
class GmailControllersTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired GmailTokenStore tokens;
    @MockitoBean GmailClient google;

    Team team;
    WebSession browser;

    @BeforeEach
    void signIn() throws Exception {
        team = accounts.team(true);
        browser = WebSession.signIn(mvc, team.employee().email(), TestAccounts.PASSWORD);
    }

    String startConsent() throws Exception {
        MockHttpServletResponse response = mvc.perform(browser.cookies(get("/oauth2/authorization/google")))
                .andExpect(status().isFound()).andReturn().getResponse();
        browser.remember(response);
        return response.getRedirectedUrl();
    }

    @Test
    void connectRedirectsToGoogleConsentForOfflineGmailAccess() throws Exception {
        var query = UriComponentsBuilder.fromUri(URI.create(startConsent())).build().getQueryParams();

        assertThat(startConsent()).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(query.getFirst("client_id")).isEqualTo("test-client-id");
        assertThat(java.net.URLDecoder.decode(query.getFirst("redirect_uri"), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("http://localhost:3000/login/oauth2/code/google");
        assertThat(query.getFirst("access_type")).isEqualTo("offline");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(java.net.URLDecoder.decode(query.getFirst("scope"), java.nio.charset.StandardCharsets.UTF_8))
                .contains("https://www.googleapis.com/auth/gmail.readonly");
        assertThat(query.getFirst("state")).isNotBlank();
    }

    @Test
    void callbackWithTheRightStateStoresTheEncryptedToken() throws Exception {
        String state = UriComponentsBuilder.fromUri(URI.create(startConsent())).build().getQueryParams().getFirst("state");
        given(google.exchangeCode("auth-code", "http://localhost:3000/login/oauth2/code/google"))
                .willReturn(new GmailClient.Tokens("ya29.access", "1//refresh"));
        given(google.profileAddress("ya29.access")).willReturn("employee@gmail.example");

        mvc.perform(browser.cookies(get("/login/oauth2/code/google").param("code", "auth-code").param("state", state)))
                .andExpect(redirectedUrl("/settings?gmail=connected"));

        assertThat(tokens.refreshToken(team.employee().userId())).contains("1//refresh");
        mvc.perform(browser.cookies(get("/api/v1/integrations/gmail")))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.address").value("employee@gmail.example"))
                .andExpect(jsonPath("$.lastPolledAt").isEmpty());
    }

    @Test
    void disabledGmailApiIsExplainedOnSettings() throws Exception {
        String state = UriComponentsBuilder.fromUri(URI.create(startConsent())).build().getQueryParams().getFirst("state");
        given(google.exchangeCode("auth-code", "http://localhost:3000/login/oauth2/code/google"))
                .willReturn(new GmailClient.Tokens("ya29.access", "1//refresh"));
        String body = "{\"error\":{\"code\":403,\"message\":\"Gmail API has not been used in project 797327540964 before or it is disabled.\","
                + "\"status\":\"PERMISSION_DENIED\",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\","
                + "\"reason\":\"SERVICE_DISABLED\",\"domain\":\"googleapis.com\"}]}}";
        given(google.profileAddress("ya29.access")).willThrow(org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden", org.springframework.http.HttpHeaders.EMPTY,
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(browser.cookies(get("/login/oauth2/code/google").param("code", "auth-code").param("state", state)))
                .andExpect(redirectedUrl("/settings?gmail=api-disabled"));

        assertThat(tokens.refreshToken(team.employee().userId())).isEmpty();
    }

    @Test
    void callbackWithAForeignStateIsRefused() throws Exception {
        startConsent();

        mvc.perform(browser.cookies(get("/login/oauth2/code/google").param("code", "auth-code").param("state", "forged")))
                .andExpect(redirectedUrl("/settings?gmail=error"));

        verify(google, never()).exchangeCode(anyString(), anyString());
        assertThat(tokens.refreshToken(team.employee().userId())).isEmpty();
    }

    @Test
    void deniedConsentRedirectsWithAnError() throws Exception {
        startConsent();

        mvc.perform(browser.cookies(get("/login/oauth2/code/google").param("error", "access_denied")))
                .andExpect(redirectedUrl("/settings?gmail=error"));
    }

    @Test
    void disconnectForgetsTheToken() throws Exception {
        tokens.save(team.employee().userId(), "1//refresh", "employee@gmail.example");

        mvc.perform(browser.withCsrf(post("/api/v1/integrations/gmail/disconnect"))).andExpect(status().isNoContent());

        verify(google).revoke("1//refresh");
        mvc.perform(browser.cookies(get("/api/v1/integrations/gmail")))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.address").isEmpty());
    }

    @Test
    void connectNeedsASignedInUser() throws Exception {
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isUnauthorized());
    }

    @Test
    void emailsListIsTheUsersOwn() throws Exception {
        mvc.perform(browser.cookies(get("/api/v1/emails"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

}
