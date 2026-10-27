package com.obsidian.codeguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.auth.AccountService;
import com.obsidian.firewall.EventPublisher;
import com.obsidian.model.UserRepository;
import com.obsidian.scanner.VirusTotalClient;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration(proxyBeanMethods = false)
class CodeGuardConfig {

    @Bean
    GitHubAppClient gitHubAppClient(CodeGuardProperties config, ObjectMapper mapper, Clock clock) {
        return new GitHubAppClient(config, mapper, clock);
    }

    @Bean
    RepoScanner repoScanner(CodeGuardProperties config, ObjectMapper mapper) {
        return new RepoScanner(new ToolRunner(), mapper, config);
    }

    @Bean
    CodeGuardService codeGuardService(GitHubAppClient github, RepoScanner scanner, VirusTotalClient virusTotal,
            CodeFindingRepository findings, EventPublisher events, UserRepository users, AccountService accounts,
            CodeGuardProperties config) {
        return new CodeGuardService(github, scanner, virusTotal, findings, events, users, accounts, config);
    }

    /** GitHub gives webhooks 10 s: the request returns 202 and scans run here, one virtual thread each. */
    @Bean(destroyMethod = "close")
    ExecutorService codeGuardExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @EventListener(ApplicationReadyEvent.class)
    void announce(ApplicationReadyEvent ready) {
        CodeGuardProperties config = ready.getApplicationContext().getBean(CodeGuardProperties.class);
        if (!config.enabled()) {
            LoggerFactory.getLogger(CodeGuardConfig.class).info("Code Guard is off: set GITHUB_APP_ID, "
                    + "GITHUB_APP_PRIVATE_KEY_PATH, GITHUB_WEBHOOK_SECRET and CODEGUARD_ORG_ADMIN_EMAIL to turn it on");
        }
    }
}
