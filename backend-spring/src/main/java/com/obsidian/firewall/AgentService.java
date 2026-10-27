package com.obsidian.firewall;

import com.obsidian.audit.AuditService;
import com.obsidian.auth.Tokens;
import com.obsidian.config.ForbiddenException;
import com.obsidian.model.Account;
import com.obsidian.model.AuditAction;
import com.obsidian.model.EndpointAgent;
import com.obsidian.model.EndpointAgentRepository;
import com.obsidian.rules.InvalidRuleRequestException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers Rust daemons. The bearer token exists only in the return value; only its SHA-256 is stored. */
@Service
public class AgentService {

    private static final Pattern HOSTNAME =
            Pattern.compile("^(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*$");

    public record Registered(EndpointAgent agent, String token) {}

    private final EndpointAgentRepository agents;
    private final AuditService audit;

    public AgentService(EndpointAgentRepository agents, AuditService audit) {
        this.agents = agents;
        this.audit = audit;
    }

    @Transactional
    public Registered register(Account account, String rawHostname) {
        if (!account.isAdmin()) {
            throw new ForbiddenException("Only organization admins can register agents.");
        }
        String hostname = rawHostname == null ? "" : rawHostname.trim().toLowerCase(Locale.ROOT);
        if (!HOSTNAME.matcher(hostname).matches()) {
            throw new InvalidRuleRequestException("Enter a hostname: letters, digits, hyphens and dots only.");
        }
        String token = Tokens.generate("obs_");
        EndpointAgent agent = agents.save(new EndpointAgent(account.orgId(), hostname, Tokens.sha256Hex(token)));
        audit.record(AuditAction.AGENT_REGISTER, account.orgId(), account.userId(), null,
                Map.of("agentId", agent.getId().toString(), "hostname", hostname));
        return new Registered(agent, token);
    }
}
