package com.obsidian.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.auth.AccountService;
import com.obsidian.auth.Tokens;
import com.obsidian.firewall.AgentService;
import com.obsidian.model.Account;
import com.obsidian.model.EndpointAgentRepository;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.UserRepository;
import com.obsidian.rules.RuleService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Optional startup setup used by {@code scripts/build.sh run}, set only by startup properties (never
 * reachable over HTTP):
 * <ul>
 *   <li>{@code obsidian.bootstrap.rules}: a JSON file of reports and bypasses (the official demo's
 *       {@code demo/rules.json}), applied through RuleService as each named account, so every rule is
 *       audited and published like a real change;</li>
 *   <li>{@code obsidian.bootstrap.agent-token-file} (+ {@code agent-admin}, {@code agent-hostname}):
 *       the local daemon's token. Kept while this database knows it, otherwise a new agent is registered
 *       in the admin's organization and the token written there (0600).</li>
 * </ul>
 * Done in-process because a script can't sign in through Turnstile, by design.
 */
@Component
public class StartupBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupBootstrap.class);

    record RuleSpec(String as, String action, IndicatorType indicatorType, String indicatorValue,
            ThreatCategory threatCategory, Integer ttlMinutes) {}

    private final RuleService rules;
    private final AgentService agentService;
    private final EndpointAgentRepository agents;
    private final UserRepository users;
    private final AccountService accounts;
    private final ObjectMapper mapper;
    private final String rulesFile;
    private final String tokenFile;
    private final String agentAdmin;
    private final String agentHostname;

    public StartupBootstrap(RuleService rules, AgentService agentService, EndpointAgentRepository agents,
            UserRepository users, AccountService accounts, ObjectMapper mapper,
            @Value("${obsidian.bootstrap.rules:}") String rulesFile,
            @Value("${obsidian.bootstrap.agent-token-file:}") String tokenFile,
            @Value("${obsidian.bootstrap.agent-admin:}") String agentAdmin,
            @Value("${obsidian.bootstrap.agent-hostname:localhost}") String agentHostname) {
        this.rules = rules;
        this.agentService = agentService;
        this.agents = agents;
        this.users = users;
        this.accounts = accounts;
        this.mapper = mapper;
        this.rulesFile = rulesFile;
        this.tokenFile = tokenFile;
        this.agentAdmin = agentAdmin;
        this.agentHostname = agentHostname;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!rulesFile.isBlank()) {
            applyRules(Path.of(rulesFile));
        }
        if (!tokenFile.isBlank()) {
            ensureAgentToken(Path.of(tokenFile), agentAdmin, agentHostname);
        }
    }

    /** Reports and bypasses are upserts, so applying the same file again changes nothing but timestamps. */
    public void applyRules(Path file) {
        List<RuleSpec> specs;
        try {
            specs = List.of(mapper.readValue(file.toFile(), RuleSpec[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("Reading " + file + " failed", e);
        }
        for (RuleSpec spec : specs) {
            Account account = account(spec.as());
            if ("BYPASS".equals(spec.action())) {
                rules.bypass(account, spec.indicatorType(), spec.indicatorValue(), spec.ttlMinutes());
            } else {
                rules.report(account, spec.indicatorType(), spec.indicatorValue(), spec.threatCategory());
            }
        }
        log.info("Startup setup: applied {} threat rules from {}", specs.size(), file);
    }

    public void ensureAgentToken(Path file, String adminEmail, String hostname) {
        Account admin = account(adminEmail);
        if (!admin.isAdmin()) {
            throw new IllegalStateException(adminEmail + " must be an organization admin to own the local agent");
        }
        try {
            if (Files.isRegularFile(file)) {
                String existing = Files.readString(file).trim();
                if (agents.findByTokenHash(Tokens.sha256Hex(existing)).isPresent()) {
                    log.info("Startup setup: reusing the agent token in {}", file);
                    return;
                }
            }
            AgentService.Registered registered = agentService.register(admin, hostname);
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), ".agent", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.writeString(temp, registered.token() + "\n");
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Startup setup: registered agent {} for {}; token written to {}",
                    registered.agent().getHostname(), adminEmail, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Writing the agent token to " + file + " failed", e);
        }
    }

    private Account account(String email) {
        return users.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT)).map(accounts::of)
                .orElseThrow(() -> new IllegalStateException("Startup setup: no account " + email
                        + " (is the right seed loaded?)"));
    }
}
