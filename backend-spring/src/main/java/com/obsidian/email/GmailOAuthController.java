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
import org.springframework.web.client.HttpClientErrorException;

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
        } catch (HttpClientErrorException.Forbidden e) {
            if (apiDisabled(e)) {
                log.warn("Connecting Gmail failed: the Gmail API is disabled in the Google Cloud project of "
                        + "GMAIL_CLIENT_ID; enable it (APIs & Services → Library → Gmail API) and connect again");
                return redirect("/settings?gmail=api-disabled");
            }
            log.warn("Connecting Gmail failed: Google answered 403 ({})", e.getStatusText());
            return redirect("/settings?gmail=error");
        } catch (RuntimeException e) {
            String reason = String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
            log.warn("Connecting Gmail failed: {}: {}", e.getClass().getSimpleName(),
                    reason.length() > 200 ? reason.substring(0, 200) + "…" : reason);
            return redirect("/settings?gmail=error");
        }
    }

    /** Google's 403 when the Gmail API isn't enabled in the OAuth client's project. */
    private static boolean apiDisabled(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        return body.contains("SERVICE_DISABLED") || body.contains("accessNotConfigured");
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }
}
