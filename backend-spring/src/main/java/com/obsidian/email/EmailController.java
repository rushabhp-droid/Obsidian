package com.obsidian.email;

import com.obsidian.model.Account;
import com.obsidian.model.ScannedEmailRepository;
import com.obsidian.model.User;
import com.obsidian.model.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard reads for the Email security and Settings pages. */
@RestController
public class EmailController {

    private static final Logger log = LoggerFactory.getLogger(EmailController.class);

    private final ScannedEmailRepository emails;
    private final UserRepository users;
    private final GmailTokenStore tokens;
    private final GmailClient google;

    public EmailController(ScannedEmailRepository emails, UserRepository users, GmailTokenStore tokens,
            GmailClient google) {
        this.emails = emails;
        this.users = users;
        this.tokens = tokens;
        this.google = google;
    }

    @GetMapping("/api/v1/emails")
    public List<ScannedEmailDto> list(Account account) {
        return emails.findByUserIdOrderByReceivedAtDesc(account.userId(), Limit.of(100)).stream()
                .map(ScannedEmailDto::of).toList();
    }

    @GetMapping("/api/v1/integrations/gmail")
    public GmailConnection gmail(Account account) {
        User user = users.findById(account.userId()).orElseThrow();
        boolean connected = users.findIdsWithGmailConnected().contains(user.getId());
        return new GmailConnection(connected, connected ? user.getGmailAddress() : null,
                connected ? user.getGmailLastPolledAt() : null);
    }

    @PostMapping("/api/v1/integrations/gmail/disconnect")
    public ResponseEntity<Void> disconnect(Account account) {
        var token = tokens.refreshToken(account.userId());
        tokens.clear(account.userId());
        token.ifPresent(refresh -> {
            try {
                google.revoke(refresh);
            } catch (RuntimeException e) {
                log.warn("Revoking the Gmail token at Google failed; it is deleted here anyway: {}", e.getMessage());
            }
        });
        return ResponseEntity.noContent().build();
    }
}
