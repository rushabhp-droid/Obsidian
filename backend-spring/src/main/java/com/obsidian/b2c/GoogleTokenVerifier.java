package com.obsidian.b2c;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Checks a Google ID token from the app's Credential Manager sign-in: signature
 * against Google's published keys, issuer, expiry, and an audience of our web
 * OAuth client (obsidian.b2c.google-client-id). Only a verified Google email counts.
 */
@Component
public class GoogleTokenVerifier {

    static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");

    /** What we take from a verified ID token. */
    public record GoogleIdentity(String subject, String email, String name) {}

    private final B2cProperties properties;
    private volatile JwtDecoder decoder;

    public GoogleTokenVerifier(B2cProperties properties) {
        this.properties = properties;
    }

    public GoogleIdentity verify(String idToken) {
        if (!properties.googleConfigured()) {
            throw new B2cException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                    "Google sign-in isn't set up on this server. Use email and password.");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            throw new B2cException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                    "Google sign-in couldn't be verified. Try again.");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new B2cException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                    "Your Google account has no verified email address.");
        }
        return new GoogleIdentity(jwt.getSubject(), email.trim().toLowerCase(Locale.ROOT), jwt.getClaimAsString("name"));
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current == null) {
            NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
            nimbus.setJwtValidator(validator(properties.googleClientId()));
            decoder = current = nimbus;
        }
        return current;
    }

    static OAuth2TokenValidator<Jwt> validator(String clientId) {
        OAuth2TokenValidator<Jwt> issuer = jwt -> jwt.getIssuer() != null && ISSUERS.contains(jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not issued by Google", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience);
    }
}
