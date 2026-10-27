package com.obsidian.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.obsidian.auth.AccountService;
import com.obsidian.config.ObsidianProperties;
import com.obsidian.model.Role;
import com.obsidian.model.User;
import com.obsidian.model.UserRepository;
import com.obsidian.scanner.ScanService;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import java.io.IOException;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Google's OAuth and Gmail APIs are stubbed with their real response shapes. */
class GmailPollingWorkerTest extends IntegrationTest {

    static final String GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me";
    /** users.messages.list for unread INBOX + SPAM, query strictly percent-encoded (RFC 3986). */
    static final String LIST = GMAIL + "/messages?q=is%3Aunread%20%28in%3Ainbox%20OR%20in%3Aspam%29"
            + "&maxResults=20&includeSpamTrash=true";

    @Autowired GmailTokenStore tokens;
    @Autowired ScanService scans;
    @Autowired AccountService accounts;
    @Autowired UserRepository users;
    @Autowired com.obsidian.model.ScannedEmailRepository scannedEmails;
    @Autowired TestAccounts testAccounts;
    @Autowired ObsidianProperties properties;
    @Autowired JdbcTemplate jdbc;

    static String raw(String fixture) throws IOException {
        try (var in = GmailPollingWorkerTest.class.getResourceAsStream("/emails/" + fixture)) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(in.readAllBytes());
        }
    }

    @Test
    void pollScansNewUnreadMessagesOnceAndRecordsThePollTime() throws IOException {
        User user = testAccounts.user("poll-" + UUID.randomUUID().toString().substring(0, 8) + "@aster.example",
                Role.EMPLOYEE, null);
        tokens.save(user.getId(), "1//refresh", "poll@gmail.example");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer google = MockRestServiceServer.bindTo(builder).build();
        GmailPollingWorker worker = new GmailPollingWorker(new GmailClient(properties, builder), tokens, scans, accounts,
                users, scannedEmails, properties, Clock.systemUTC());
        String first = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);

        google.expect(once(), requestTo(GmailClient.TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("grant_type", "refresh_token", "refresh_token", "1//refresh",
                        "client_id", "test-client-id")))
                .andRespond(withSuccess("{\"access_token\":\"ya29.access\",\"expires_in\":3599,\"scope\":\"x\","
                        + "\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
        google.expect(once(), requestTo(LIST))
                .andExpect(header("Authorization", "Bearer ya29.access"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"" + first + "\",\"threadId\":\"t1\"}],"
                        + "\"resultSizeEstimate\":1}", MediaType.APPLICATION_JSON));
        google.expect(once(), requestTo(GMAIL + "/messages/" + first + "?format=raw"))
                .andRespond(withSuccess("{\"id\":\"" + first + "\",\"raw\":\"" + raw("official.eml") + "\"}",
                        MediaType.APPLICATION_JSON));
        // Second poll: same message still unread; it must not be fetched again.
        google.expect(once(), requestTo(GmailClient.TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"ya29.access\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        google.expect(once(), requestTo(LIST))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"" + first + "\",\"threadId\":\"t1\"}]}",
                        MediaType.APPLICATION_JSON));

        worker.poll(user.getId());
        worker.poll(user.getId());

        google.verify();
        assertThat(jdbc.queryForObject("select count(*) from scanned_emails where user_id = ?", Integer.class, user.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select verified_official from scanned_emails where gmail_message_id = ?",
                Boolean.class, first)).isTrue();
        assertThat(jdbc.queryForObject("select gmail_last_polled_at from users where id = ?", java.sql.Timestamp.class,
                user.getId())).isNotNull();
    }

    @Test
    void emptyInboxStillCountsAsAPoll() {
        User user = testAccounts.user("empty-" + UUID.randomUUID().toString().substring(0, 8) + "@aster.example",
                Role.CONSUMER, null);
        tokens.save(user.getId(), "1//refresh", "empty@gmail.example");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer google = MockRestServiceServer.bindTo(builder).build();
        GmailPollingWorker worker = new GmailPollingWorker(new GmailClient(properties, builder), tokens, scans, accounts,
                users, scannedEmails, properties, Clock.systemUTC());
        google.expect(requestTo(GmailClient.TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"ya29.a\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        google.expect(requestTo(LIST))
                .andExpect(queryParam("maxResults", "20"))
                .andRespond(withSuccess("{\"resultSizeEstimate\":0}", MediaType.APPLICATION_JSON));

        worker.poll(user.getId());

        assertThat(jdbc.queryForObject("select gmail_last_polled_at from users where id = ?", java.sql.Timestamp.class,
                user.getId())).isNotNull();
    }
}
