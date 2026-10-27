package com.obsidian.firewall;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.firewall.EventEnvelope.Kind;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes on Redis channel:firewall:events. Every Spring instance's
 * RedisPubSubListener relays it to its connected daemons and dashboards.
 */
@Component
public class EventPublisher {

    public static final String CHANNEL = "channel:firewall:events";
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public EventPublisher(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /** Never throws: the change is already committed, so a Redis outage is logged, not surfaced. */
    public void publish(Kind kind, UUID orgId, UUID userId, Object event) {
        try {
            EventEnvelope envelope = new EventEnvelope(kind, orgId, userId, mapper.valueToTree(event));
            redis.convertAndSend(CHANNEL, mapper.writeValueAsString(envelope));
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Publishing {} event on {} failed", kind, CHANNEL, e);
        }
    }
}
