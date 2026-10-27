package com.obsidian.b2c;

import com.obsidian.auth.Tokens;
import com.obsidian.b2c.CodeMailer.Purpose;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * One-time 6-digit email codes for sign-up and password reset. Redis keeps only
 * a hash of the code (b2c:code:&lt;purpose&gt;:&lt;email&gt;) for obsidian.b2c.code-ttl,
 * allows 5 wrong guesses, and a new code at most every obsidian.b2c.resend-after.
 */
@Service
public class EmailCodeService {

    static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final CodeMailer mailer;
    private final B2cProperties properties;

    public EmailCodeService(StringRedisTemplate redis, CodeMailer mailer, B2cProperties properties) {
        this.redis = redis;
        this.mailer = mailer;
        this.properties = properties;
    }

    /**
     * Emails a new code. When one went out moments ago, {@code quiet} skips this
     * one silently (sign-in retries); otherwise the caller gets a 429.
     */
    public void send(String email, String name, Purpose purpose, boolean quiet) {
        Boolean first = redis.opsForValue().setIfAbsent(cooldownKey(purpose, email), "1", properties.resendAfter());
        if (!Boolean.TRUE.equals(first)) {
            if (quiet) {
                return;
            }
            throw new B2cException(HttpStatus.TOO_MANY_REQUESTS, "TOO_SOON",
                    "We just sent a code. Wait a few seconds before asking for another one.");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redis.delete(codeKey(purpose, email));
        redis.opsForHash().putAll(codeKey(purpose, email), Map.of("hash", hash(email, code), "attempts", "0"));
        redis.expire(codeKey(purpose, email), properties.codeTtl());
        try {
            mailer.send(email, name, code, purpose);
        } catch (RuntimeException e) {
            redis.delete(cooldownKey(purpose, email)); // let them retry right away
            throw e;
        }
    }

    /** Uses up the code when it matches; throws {@link B2cException#invalidCode()} otherwise. */
    public void verify(String email, Purpose purpose, String code) {
        String key = codeKey(purpose, email);
        Object stored = redis.opsForHash().get(key, "hash");
        if (stored == null || code == null) {
            throw B2cException.invalidCode();
        }
        Long attempts = redis.opsForHash().increment(key, "attempts", 1);
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redis.delete(key);
            throw B2cException.invalidCode();
        }
        boolean matches = MessageDigest.isEqual(stored.toString().getBytes(StandardCharsets.UTF_8),
                hash(email, code.trim()).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw B2cException.invalidCode();
        }
        redis.delete(key);
    }

    private static String hash(String email, String code) {
        return Tokens.sha256Hex(email + ":" + code);
    }

    private static String codeKey(Purpose purpose, String email) {
        return "b2c:code:" + purpose.name().toLowerCase() + ":" + email;
    }

    private static String cooldownKey(Purpose purpose, String email) {
        return "b2c:code-sent:" + purpose.name().toLowerCase() + ":" + email;
    }
}
