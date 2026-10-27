package com.obsidian.b2c;

import com.obsidian.auth.MobileToken;
import com.obsidian.auth.Tokens;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * B2C app bearer tokens: obc_ + 32 random bytes. Redis holds
 * b2c:token:&lt;sha256&gt; → consumer id for 30 days. The obc_ and obm_ prefixes
 * keep consumer and dashboard tokens from ever resolving on the other API.
 */
@Service
public class ConsumerTokenService {

    static final String PREFIX = "obc_";
    static final Duration TTL = Duration.ofDays(30);
    private static final String KEY = "b2c:token:";

    private final StringRedisTemplate redis;
    private final ConsumerRepository consumers;
    private final Clock clock;

    public ConsumerTokenService(StringRedisTemplate redis, ConsumerRepository consumers, Clock clock) {
        this.redis = redis;
        this.consumers = consumers;
        this.clock = clock;
    }

    public MobileToken issue(Consumer consumer) {
        String token = Tokens.generate(PREFIX);
        redis.opsForValue().set(KEY + Tokens.sha256Hex(token), consumer.getId().toString(), TTL);
        return new MobileToken(token, clock.instant().plus(TTL));
    }

    public Optional<Consumer> resolve(String token) {
        if (token == null || !token.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String id = redis.opsForValue().get(KEY + Tokens.sha256Hex(token));
        return id == null ? Optional.empty() : consumers.findById(UUID.fromString(id));
    }

    public void revoke(String token) {
        redis.delete(KEY + Tokens.sha256Hex(token));
    }
}
