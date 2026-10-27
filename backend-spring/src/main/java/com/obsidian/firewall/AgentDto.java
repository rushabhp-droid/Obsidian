package com.obsidian.firewall;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.obsidian.model.EndpointAgent;
import java.time.Instant;
import java.util.UUID;

/** EndpointAgent / RegisteredAgent in types.ts; the token is present only in the registration response. */
public record AgentDto(UUID id, String hostname, Instant lastHeartbeatAt, Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String token) {

    static AgentDto of(EndpointAgent agent, String token) {
        return new AgentDto(agent.getId(), agent.getHostname(), agent.getLastHeartbeatAt(), agent.getCreatedAt(), token);
    }
}
