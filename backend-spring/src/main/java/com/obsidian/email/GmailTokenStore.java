package com.obsidian.email;

import com.obsidian.config.ObsidianProperties;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Gmail refresh tokens in users.gmail_refresh_token_enc, encrypted by Postgres
 * with pgp_sym_encrypt and APP_ENC_KEY. The only code that reads or writes them.
 */
@Component
public class GmailTokenStore {

    private final JdbcTemplate jdbc;
    private final String key;

    public GmailTokenStore(JdbcTemplate jdbc, ObsidianProperties properties) {
        this.jdbc = jdbc;
        this.key = properties.encKey();
    }

    public void save(UUID userId, String refreshToken, String address) {
        jdbc.update("update users set gmail_refresh_token_enc = pgp_sym_encrypt(?, ?), gmail_address = ? where id = ?",
                refreshToken, key(), address, userId);
    }

    public Optional<String> refreshToken(UUID userId) {
        return jdbc.queryForList("select pgp_sym_decrypt(gmail_refresh_token_enc, ?) from users"
                + " where id = ? and gmail_refresh_token_enc is not null", String.class, key(), userId).stream().findFirst();
    }

    public void markPolled(UUID userId, Instant at) {
        jdbc.update("update users set gmail_last_polled_at = ? where id = ?", Timestamp.from(at), userId);
    }

    public void clear(UUID userId) {
        jdbc.update("update users set gmail_refresh_token_enc = null, gmail_address = null, gmail_last_polled_at = null"
                + " where id = ?", userId);
    }

    private String key() {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("APP_ENC_KEY is not set: Gmail tokens can't be encrypted.");
        }
        return key;
    }
}
