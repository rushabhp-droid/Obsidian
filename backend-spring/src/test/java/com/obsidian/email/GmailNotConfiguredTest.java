package com.obsidian.email;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.WebSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Without GMAIL_CLIENT_ID / GMAIL_CLIENT_SECRET, Google would answer "Access blocked: missing client_id". */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"obsidian.gmail.client-id=", "obsidian.gmail.client-secret="})
class GmailNotConfiguredTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;

    @Test
    void connectExplainsInsteadOfSendingTheUserToGoogle() throws Exception {
        WebSession browser = WebSession.signIn(mvc, accounts.team(true).admin().email(), TestAccounts.PASSWORD);

        mvc.perform(browser.cookies(get("/oauth2/authorization/google")))
                .andExpect(redirectedUrl("/settings?gmail=not-configured"));
    }
}
