package com.obsidian.firewall;

import com.obsidian.auth.Tokens;
import com.obsidian.model.EndpointAgent;
import com.obsidian.model.EndpointAgentRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** /ws/firewall handshake: Authorization: Bearer obs_… must hash to a registered agent, or 401. */
class AgentHandshakeInterceptor implements HandshakeInterceptor {

    static final String AGENT = "obsidian.agent";

    private final EndpointAgentRepository agents;

    AgentHandshakeInterceptor(EndpointAgentRepository agents) {
        this.agents = agents;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Map<String, Object> attributes) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        Optional<EndpointAgent> agent = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                ? agents.findByTokenHash(Tokens.sha256Hex(header.substring(7).trim()))
                : Optional.empty();
        if (agent.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(AGENT, agent.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Exception exception) {}
}
