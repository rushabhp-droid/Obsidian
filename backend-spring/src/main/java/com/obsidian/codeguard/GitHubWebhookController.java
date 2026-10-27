package com.obsidian.codeguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/webhooks/github. The X-Hub-Signature-256 HMAC is checked on the raw bytes before
 * anything is parsed; then 202 right away and the scan runs on a virtual thread.
 */
@RestController
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final CodeGuardProperties config;
    private final WebhookSignatureVerifier verifier;
    private final CodeGuardService codeGuard;
    private final ExecutorService executor;
    private final ObjectMapper mapper;

    public GitHubWebhookController(CodeGuardProperties config, CodeGuardService codeGuard,
            @Qualifier("codeGuardExecutor") ExecutorService executor, ObjectMapper mapper) {
        this.config = config;
        this.verifier = new WebhookSignatureVerifier(config.webhookSecret());
        this.codeGuard = codeGuard;
        this.executor = executor;
        this.mapper = mapper;
    }

    @PostMapping("/api/v1/webhooks/github")
    public ResponseEntity<Void> receive(@RequestBody(required = false) byte[] body,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(name = "X-GitHub-Event", required = false) String event) {
        if (!config.enabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (!verifier.verify(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode payload;
        try {
            payload = mapper.readTree(body);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
        String name = event == null ? "" : event;
        executor.execute(() -> {
            try {
                codeGuard.handle(name, payload);
            } catch (RuntimeException e) {
                log.warn("Code Guard {} scan of {} failed: {}", name, payload.path("repository").path("full_name").asText(),
                        e.getMessage());
            }
        });
        return ResponseEntity.accepted().build();
    }
}
