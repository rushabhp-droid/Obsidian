package com.obsidian.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under {@code obsidian.*} in application.yml. Secrets come from env. */
@ConfigurationProperties("obsidian")
public record ObsidianProperties(
        String publicBaseUrl,
        List<String> allowedOrigins,
        String encKey,
        WebAuthn webauthn,
        Turnstile turnstile,
        VirusTotal virustotal,
        Gmail gmail,
        Email email) {

    public record WebAuthn(String rpId, String rpName) {}

    public record Turnstile(String secretKey, String verifyUrl) {
        public boolean enabled() {
            return secretKey != null && !secretKey.isBlank();
        }
    }

    public record VirusTotal(String apiKey, String baseUrl, int perMinute, int perDay) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Gmail(String clientId, String clientSecret, String scopes, Duration pollInterval,
            int maxMessagesPerPoll) {
        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public record Email(List<String> officialDomains) {}

    /** Origins a browser may open /ws/dashboard and WebAuthn ceremonies from. */
    public List<String> browserOrigins() {
        var origins = new java.util.ArrayList<String>();
        origins.add(publicBaseUrl);
        if (allowedOrigins != null) {
            origins.addAll(allowedOrigins);
        }
        return List.copyOf(new java.util.LinkedHashSet<>(origins));
    }
}
