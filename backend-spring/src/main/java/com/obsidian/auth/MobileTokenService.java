package com.obsidian.auth;

import com.obsidian.model.User;
import com.obsidian.model.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Android bearer tokens. Redis holds mobile:token:&lt;sha256&gt; → user id for
 * 30 days; the token itself is only ever in the app's Keystore-backed storage.
 */
@Service
public class MobileTokenService {

    static final String PREFIX = "obm_";
    static final Duration TTL = Duration.ofDays(30);
    private static final String KEY = "mobile:token:";

    private final StringRedisTemplate redis;
    private final UserRepository users;
    private final Clock clock;

    public MobileTokenService(StringRedisTemplate redis, UserRepository users, Clock clock) {
        this.redis = redis;
        this.users = users;
        this.clock = clock;
    }

    public MobileToken issue(User user) {
        String token = Tokens.generate(PREFIX);
        redis.opsForValue().set(KEY + Tokens.sha256Hex(token), user.getId().toString(), TTL);
        return new MobileToken(token, clock.instant().plus(TTL));
    }

    public Optional<User> resolve(String token) {
        if (token == null || !token.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String userId = redis.opsForValue().get(KEY + Tokens.sha256Hex(token));
        return userId == null ? Optional.empty() : users.findById(UUID.fromString(userId));
    }

    public void revoke(String token) {
        redis.delete(KEY + Tokens.sha256Hex(token));
    }
}
