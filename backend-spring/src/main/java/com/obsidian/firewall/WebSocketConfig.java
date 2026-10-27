package com.obsidian.firewall;

import com.obsidian.auth.AccountService;
import com.obsidian.config.ObsidianProperties;
import com.obsidian.model.EndpointAgentRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    private final FirewallWebSocketHandler daemons;
    private final DashboardWebSocketHandler dashboards;
    private final EndpointAgentRepository agents;
    private final AccountService accounts;
    private final ObsidianProperties properties;

    WebSocketConfig(FirewallWebSocketHandler daemons, DashboardWebSocketHandler dashboards,
            EndpointAgentRepository agents, AccountService accounts, ObsidianProperties properties) {
        this.daemons = daemons;
        this.dashboards = dashboards;
        this.agents = agents;
        this.accounts = accounts;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Daemons send no Origin header; their bearer token is the check.
        registry.addHandler(daemons, "/ws/firewall").addInterceptors(new AgentHandshakeInterceptor(agents));
        registry.addHandler(dashboards, "/ws/dashboard")
                .addInterceptors(new DashboardHandshakeInterceptor(accounts))
                .setAllowedOrigins(properties.browserOrigins().toArray(String[]::new));
    }
}
