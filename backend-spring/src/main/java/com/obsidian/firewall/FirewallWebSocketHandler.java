package com.obsidian.firewall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.obsidian.firewall.EventEnvelope.Kind;
import com.obsidian.model.EndpointAgent;
import com.obsidian.model.EndpointAgentRepository;
import com.obsidian.rules.FirewallRule;
import com.obsidian.rules.RuleService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * /ws/firewall: SYNC with the organization's full block set on every connect,
 * then BLOCK / BYPASS for each org-level IP or DOMAIN change. HEARTBEAT from
 * the daemon updates endpoint_agents.last_heartbeat_at and reaches dashboards.
 */
@Component
public class FirewallWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(FirewallWebSocketHandler.class);
    private static final Set<String> NETWORK_TYPES = Set.of("IP", "DOMAIN");

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final RuleService rules;
    private final EndpointAgentRepository agents;
    private final EventPublisher events;
    private final ObjectMapper mapper;
    private final Clock clock;

    public FirewallWebSocketHandler(RuleService rules, EndpointAgentRepository agents, EventPublisher events,
            ObjectMapper mapper, Clock clock) {
        this.rules = rules;
        this.agents = agents;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5_000, 512 * 1024);
        sessions.put(session.getId(), safe);
        EndpointAgent agent = agent(session);
        ObjectNode sync = mapper.createObjectNode().put("type", "SYNC");
        var list = sync.putArray("rules");
        for (FirewallRule rule : rules.firewallBlocks(agent.getOrgId())) {
            list.addObject().put("ruleId", rule.ruleId().toString())
                    .put("indicator_type", rule.indicatorType().name()).put("value", rule.value());
        }
        safe.sendMessage(new TextMessage(mapper.writeValueAsString(sync)));
        heartbeat(agent);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode frame = mapper.readTree(message.getPayload());
            if ("HEARTBEAT".equals(frame.path("type").asText())) {
                heartbeat(agent(session));
            }
        } catch (IOException e) {
            log.debug("Ignoring a frame that isn't JSON from agent {}", agent(session).getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    /** Relays a rule change to this organization's daemons; personal rules and other types never go out. */
    void deliver(EventEnvelope envelope) {
        JsonNode event = envelope.event();
        if (envelope.kind() != Kind.RULE || envelope.orgId() == null || envelope.userId() != null
                || !NETWORK_TYPES.contains(event.path("indicator_type").asText())) {
            return;
        }
        ObjectNode frame = mapper.createObjectNode().put("type", event.path("type").asText())
                .put("ruleId", event.path("ruleId").asText())
                .put("indicator_type", event.path("indicator_type").asText())
                .put("value", event.path("value").asText());
        TextMessage message = new TextMessage(frame.toString());
        for (WebSocketSession session : sessions.values()) {
            if (envelope.orgId().equals(agent(session).getOrgId())) {
                send(session, message);
            }
        }
    }

    private void heartbeat(EndpointAgent agent) {
        Instant now = clock.instant();
        agents.touchHeartbeat(agent.getId(), now);
        events.publish(Kind.HEARTBEAT, agent.getOrgId(), null,
                Map.of("type", "HEARTBEAT", "agentId", agent.getId(), "hostname", agent.getHostname(), "at", now));
    }

    private static EndpointAgent agent(WebSocketSession session) {
        return (EndpointAgent) session.getAttributes().get(AgentHandshakeInterceptor.AGENT);
    }

    private static void send(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping a frame for closed socket {}", session.getId());
        }
    }
}
