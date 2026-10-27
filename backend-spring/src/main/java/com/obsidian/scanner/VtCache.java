package com.obsidian.scanner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis keys of the VirusTotal layer (docs/protocol.md, "Redis"). */
@Component
public class VtCache {

    static final Duration VERDICT_TTL = Duration.ofDays(1);
    static final Duration PENDING_TTL = Duration.ofHours(1);
    static final Duration UNKNOWN_FILE_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public VtCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public Optional<VtVerdict> get(String id) {
        String json = redis.opsForValue().get("vt:cache:" + id);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, VtVerdict.class));
        } catch (JsonProcessingException e) {
            redis.delete("vt:cache:" + id); // unreadable entry: look it up again
            return Optional.empty();
        }
    }

    public void put(String id, VtVerdict verdict) {
        try {
            redis.opsForValue().set("vt:cache:" + id, mapper.writeValueAsString(verdict), VERDICT_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<String> pendingAnalysis(String urlId) {
        return Optional.ofNullable(redis.opsForValue().get("vt:pending:" + urlId));
    }

    public void markPending(String urlId, String analysisId) {
        redis.opsForValue().set("vt:pending:" + urlId, analysisId, PENDING_TTL);
    }

    public void clearPending(String urlId) {
        redis.delete("vt:pending:" + urlId);
    }

    public boolean isUnknownFile(String sha256) {
        return Boolean.TRUE.equals(redis.hasKey("vt:notfound:" + sha256));
    }

    public void markUnknownFile(String sha256) {
        redis.opsForValue().set("vt:notfound:" + sha256, "1", UNKNOWN_FILE_TTL);
    }
}
