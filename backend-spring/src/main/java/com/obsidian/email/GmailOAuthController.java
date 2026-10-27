package com.obsidian.email;

import com.obsidian.auth.Tokens;
import com.obsidian.config.ObsidianProperties;
import com.obsidian.model.Account;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Connect Gmail" for the signed-in user. On Spring's usual OAuth2 paths (routed
 * to Spring by the tunnel and next.config.ts), but not Spring's oauth2Login,
 * which would replace the signed-in principal with the Google account.
 */
@RestController
public class GmailOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GmailOAuthController.class);
    static final String STATE = "obsidian.gmail.oauth-state";

    private final GmailClient google;
    private final GmailTokenStore tokens;
    private final String redirectUri;
    private final ObsidianProperties.Gmail gmail;

    public GmailOAuthController(GmailClient google, GmailTokenStore tokens, ObsidianProperties properties) {
        this.google = google;
        this.tokens = tokens;
        this.redirectUri = properties.publicBaseUrl() + "/login/oauth2/code/google";
        this.gmail = properties.gmail();
    }

    @GetMapping("/oauth2/authorization/google")
    public ResponseEntity<Void> connect(Account account, HttpSession session) {
        if (!gmail.configured()) {
            // Google would only answer "Access blocked: missing client_id".
            log.warn("Connect Gmail needs GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET (README, \"Connect Gmail\")");
            return redirect("/settings?gmail=not-configured");
        }
        String state = Tokens.generate("");
        session.setAttribute(STATE, state);
        return redirect(GmailClient.authorizationUrl(gmail, state, redirectUri));
    }

    @GetMapping("/login/oauth2/code/google")
    public ResponseEntity<Void> callback(Account account, HttpSession session,
            @RequestParam(required = false) String code, @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        Object expected = session.getAttribute(STATE);
        session.removeAttribute(STATE);
        if (error != null || code == null || state == null || !(expected instanceof String saved)
                || !MessageDigest.isEqual(saved.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            return redirect("/settings?gmail=error");
        }
        try {
            GmailClient.Tokens granted = google.exchangeCode(code, redirectUri);
            if (granted.refreshToken() == null || granted.refreshToken().isBlank()) {
                return redirect("/settings?gmail=error");
            }
            tokens.save(account.userId(), granted.refreshToken(), google.profileAddress(granted.accessToken()));
            return redirect("/settings?gmail=connected");
        } catch (RuntimeException e) {
            log.warn("Connecting Gmail failed: {}", e.getMessage());
            return redirect("/settings?gmail=error");
        }
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }
}
