package com.obsidian.firewall;

import com.obsidian.config.ForbiddenException;
import com.obsidian.model.Account;
import com.obsidian.model.EndpointAgent;
import com.obsidian.model.EndpointAgentRepository;
import com.obsidian.rules.FirewallRule;
import com.obsidian.rules.RuleService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Registers Rust daemons. The bearer token is returned once; only its SHA-256 is stored. */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    public record RegisterAgentRequest(@NotBlank String hostname) {}

    private final AgentService agentService;
    private final EndpointAgentRepository agents;
    private final RuleService rules;

    public AgentController(AgentService agentService, EndpointAgentRepository agents, RuleService rules) {
        this.agentService = agentService;
        this.agents = agents;
        this.rules = rules;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentDto register(Account account, @RequestBody RegisterAgentRequest request) {
        AgentService.Registered registered = agentService.register(account, request.hostname());
        return AgentDto.of(registered.agent(), registered.token());
    }

    /** One daemon and the block set it enforces (its organization's SYNC: org + global DOMAIN/IP rules). */
    public record AgentDetail(AgentDto agent, List<FirewallRule> enforced) {}

    @GetMapping("/{id}")
    public AgentDetail detail(Account account, @PathVariable UUID id) {
        if (!account.isAdmin()) {
            throw new ForbiddenException("Only organization admins can inspect endpoints.");
        }
        EndpointAgent agent = agents.findById(id).filter(a -> a.getOrgId().equals(account.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such agent in your organization."));
        return new AgentDetail(AgentDto.of(agent, null), rules.firewallBlocks(account.orgId()));
    }

    @GetMapping
    public List<AgentDto> list(Account account) {
        if (!account.inOrganization()) {
            return List.of();
        }
        return agents.findByOrgIdOrderByCreatedAtDesc(account.orgId()).stream().map(a -> AgentDto.of(a, null)).toList();
    }
}
