package com.obsidian.firewall;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Subscribes to channel:firewall:events and fans each event out to this instance's sockets. */
@Configuration(proxyBeanMethods = false)
class RedisPubSubListener {

    private static final Logger log = LoggerFactory.getLogger(RedisPubSubListener.class);

    @Bean
    RedisMessageListenerContainer firewallEventsListener(RedisConnectionFactory connectionFactory, ObjectMapper mapper,
            FirewallWebSocketHandler daemons, DashboardWebSocketHandler dashboards) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            try {
                EventEnvelope envelope = mapper.readValue(message.getBody(), EventEnvelope.class);
                daemons.deliver(envelope);
                dashboards.deliver(envelope);
            } catch (IOException | RuntimeException e) {
                log.warn("Ignoring an unreadable message on {}: {}", EventPublisher.CHANNEL, e.getMessage());
            }
        }, new ChannelTopic(EventPublisher.CHANNEL));
        return container;
    }
}
