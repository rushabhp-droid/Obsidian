package com.obsidian.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.obsidian.model.Role;
import com.obsidian.model.User;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Gmail refresh tokens are encrypted at rest with pgp_sym_encrypt and the app key (CLAUDE.md "Secrets"). */
class GmailTokenStoreTest extends IntegrationTest {

    @Autowired GmailTokenStore store;
    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;

    User user() {
        return accounts.user("gmail-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.example", Role.CONSUMER, null);
    }

    @Test
    void refreshTokenIsStoredEncryptedAndReadBack() {
        User user = user();

        store.save(user.getId(), "1//refresh-token-secret", "avery.lin@gmail.example");

        byte[] stored = jdbc.queryForObject("select gmail_refresh_token_enc from users where id = ?", byte[].class, user.getId());
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("refresh-token-secret");
        assertThat(store.refreshToken(user.getId())).contains("1//refresh-token-secret");
        assertThat(jdbc.queryForObject("select gmail_address from users where id = ?", String.class, user.getId()))
                .isEqualTo("avery.lin@gmail.example");
    }

    @Test
    void clearRemovesTheTokenAndAddress() {
        User user = user();
        store.save(user.getId(), "1//token", "a@gmail.example");

        store.clear(user.getId());

        assertThat(store.refreshToken(user.getId())).isEmpty();
        assertThat(jdbc.queryForObject("select gmail_address from users where id = ?", String.class, user.getId())).isNull();
    }
}
