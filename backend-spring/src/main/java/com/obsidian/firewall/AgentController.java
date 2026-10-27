package com.obsidian.firewall;

import com.obsidian.model.Account;
import com.obsidian.model.EndpointAgentRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registers Rust daemons. The bearer token is returned once; only its SHA-256 is stored. */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    public record RegisterAgentRequest(@NotBlank String hostname) {}

    private final AgentService agentService;
    private final EndpointAgentRepository agents;

    public AgentController(AgentService agentService, EndpointAgentRepository agents) {
        this.agentService = agentService;
        this.agents = agents;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentDto register(Account account, @RequestBody RegisterAgentRequest request) {
        AgentService.Registered registered = agentService.register(account, request.hostname());
        return AgentDto.of(registered.agent(), registered.token());
    }

    @GetMapping
    public List<AgentDto> list(Account account) {
        if (!account.inOrganization()) {
            return List.of();
        }
        return agents.findByOrgIdOrderByCreatedAtDesc(account.orgId()).stream().map(a -> AgentDto.of(a, null)).toList();
    }
}
