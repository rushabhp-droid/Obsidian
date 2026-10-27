package com.obsidian.firewall;

import com.obsidian.audit.AuditService;
import com.obsidian.auth.Tokens;
import com.obsidian.config.ForbiddenException;
import com.obsidian.model.Account;
import com.obsidian.model.AuditAction;
import com.obsidian.model.EndpointAgent;
import com.obsidian.model.EndpointAgentRepository;
import com.obsidian.rules.InvalidRuleRequestException;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
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

    private static final Pattern HOSTNAME =
            Pattern.compile("^(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*$");

    public record RegisterAgentRequest(@NotBlank String hostname) {}

    private final EndpointAgentRepository agents;
    private final AuditService audit;

    public AgentController(EndpointAgentRepository agents, AuditService audit) {
        this.agents = agents;
        this.audit = audit;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AgentDto register(Account account, @RequestBody RegisterAgentRequest request) {
        if (!account.isAdmin()) {
            throw new ForbiddenException("Only organization admins can register agents.");
        }
        String hostname = request.hostname() == null ? "" : request.hostname().trim().toLowerCase(Locale.ROOT);
        if (!HOSTNAME.matcher(hostname).matches()) {
            throw new InvalidRuleRequestException("Enter a hostname: letters, digits, hyphens and dots only.");
        }
        String token = Tokens.generate("obs_");
        EndpointAgent agent = agents.save(new EndpointAgent(account.orgId(), hostname, Tokens.sha256Hex(token)));
        audit.record(AuditAction.AGENT_REGISTER, account.orgId(), account.userId(), null,
                Map.of("agentId", agent.getId().toString(), "hostname", hostname));
        return AgentDto.of(agent, token);
    }

    @GetMapping
    public List<AgentDto> list(Account account) {
        if (!account.inOrganization()) {
            return List.of();
        }
        return agents.findByOrgIdOrderByCreatedAtDesc(account.orgId()).stream().map(a -> AgentDto.of(a, null)).toList();
    }
}
