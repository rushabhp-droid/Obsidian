package com.obsidian.email;

import com.obsidian.auth.AccountService;
import com.obsidian.config.ObsidianProperties;
import com.obsidian.model.Account;
import com.obsidian.model.ScannedEmail;
import com.obsidian.model.ScannedEmailRepository;
import com.obsidian.model.UserRepository;
import com.obsidian.model.Verdict;
import com.obsidian.scanner.ScanService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls every connected mailbox for unread INBOX and SPAM messages and hands
 * each new one, raw, to ScanService. Gmail push is [ROADMAP]. Messages already
 * in scanned_emails are skipped; a few PENDING ones are re-checked per poll.
 */
@Component
public class GmailPollingWorker {

    private static final Logger log = LoggerFactory.getLogger(GmailPollingWorker.class);
    private static final Limit PENDING_RECHECKS = Limit.of(5);

    private final GmailClient google;
    private final GmailTokenStore tokens;
    private final ScanService scans;
    private final AccountService accounts;
    private final UserRepository users;
    private final ScannedEmailRepository scannedEmails;
    private final ObsidianProperties.Gmail config;
    private final Clock clock;

    @Autowired
    public GmailPollingWorker(GmailClient google, GmailTokenStore tokens, ScanService scans, AccountService accounts,
            UserRepository users, ScannedEmailRepository scannedEmails, ObsidianProperties properties, Clock clock) {
        this.google = google;
        this.tokens = tokens;
        this.scans = scans;
        this.accounts = accounts;
        this.users = users;
        this.scannedEmails = scannedEmails;
        this.config = properties.gmail();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${obsidian.gmail.poll-interval}", initialDelayString = "${obsidian.gmail.poll-interval}")
    public void pollAll() {
        if (!config.configured()) {
            return;
        }
        for (UUID userId : users.findIdsWithGmailConnected()) {
            try {
                poll(userId);
            } catch (RuntimeException e) {
                log.warn("Gmail poll for user {} failed: {}", userId, e.getMessage());
            }
        }
    }

    public void poll(UUID userId) {
        Optional<String> refreshToken = tokens.refreshToken(userId);
        Optional<Account> account = users.findById(userId).map(accounts::of);
        if (refreshToken.isEmpty() || account.isEmpty()) {
            return;
        }
        String accessToken = google.refreshAccessToken(refreshToken.get());
        List<String> ids = google.unreadMessageIds(accessToken, config.maxMessagesPerPoll());
        for (String id : ids) {
            if (scannedEmails.existsByUserIdAndGmailMessageId(userId, id)) {
                continue;
            }
            try {
                scans.scanEmail(account.get(), id, google.rawMessage(accessToken, id));
            } catch (IllegalArgumentException e) {
                log.warn("Skipping unreadable Gmail message {}: {}", id, e.getMessage());
            }
        }
        for (ScannedEmail pending : scannedEmails.findByUserIdAndVerdictOrderByReceivedAtDesc(userId, Verdict.PENDING,
                PENDING_RECHECKS)) {
            scans.recheck(account.get(), pending);
        }
        tokens.markPolled(userId, clock.instant());
    }
}
