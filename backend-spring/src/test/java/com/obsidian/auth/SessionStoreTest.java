package com.obsidian.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import com.obsidian.support.WebSession;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Web sessions live in Redis at spring:session:sessions:<id> with a one-hour TTL. */
@AutoConfigureMockMvc
class SessionStoreTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired StringRedisTemplate redis;

    @Test
    void webSessionIsStoredInRedisForAnHour() throws Exception {
        Team team = accounts.team(true);
        WebSession browser = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);
        String sessionId = new String(Base64.getDecoder().decode(browser.sessionCookie().getValue()),
                StandardCharsets.UTF_8);

        String key = "spring:session:sessions:" + sessionId;

        assertThat(redis.hasKey(key)).isTrue();
        assertThat(redis.getExpire(key)).isBetween(3500L, 3600L);
    }
}
