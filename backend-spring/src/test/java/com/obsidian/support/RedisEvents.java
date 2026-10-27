package com.obsidian.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Records what is published on channel:firewall:events, read back from Redis itself. */
public final class RedisEvents implements AutoCloseable {

    private final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    private final List<JsonNode> received = new CopyOnWriteArrayList<>();

    public RedisEvents(RedisConnectionFactory connectionFactory, ObjectMapper mapper) {
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            try {
                received.add(mapper.readTree(message.getBody()));
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }, new ChannelTopic("channel:firewall:events"));
        container.afterPropertiesSet();
        container.start();
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(container::isListening);
    }

    public JsonNode await(Predicate<JsonNode> matching) {
        return Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> received.stream().filter(matching).findFirst().orElse(null), java.util.Objects::nonNull);
    }

    public List<JsonNode> received() {
        return List.copyOf(received);
    }

    @Override
    public void close() throws Exception {
        container.stop();
        container.destroy();
    }
}
