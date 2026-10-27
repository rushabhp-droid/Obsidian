package com.obsidian.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.obsidian.config.ObsidianProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Server-side Cloudflare Turnstile check for register and login. Off (with a
 * startup warning) when TURNSTILE_SECRET_KEY is blank, for localhost work.
 * Fails closed: a siteverify error rejects the request.
 */
@Component
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);
    private static final int MAX_TOKEN_LENGTH = 2048;

    private final ObsidianProperties.Turnstile config;
    private final RestClient http;

    public TurnstileVerifier(ObsidianProperties properties, RestClient.Builder builder) {
        this.config = properties.turnstile();
        this.http = builder.build();
    }

    public boolean enabled() {
        return config.enabled();
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnWhenOff() {
        if (!enabled()) {
            log.warn("TURNSTILE_SECRET_KEY is blank: Turnstile verification is OFF. Set it before the demo.");
        }
    }

    /** True when Turnstile is off, or Cloudflare accepted this single-use token. */
    public boolean verify(String token) {
        if (!enabled()) {
            return true;
        }
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", config.secretKey());
        form.add("response", token);
        try {
            JsonNode body = http.post().uri(config.verifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return body != null && body.path("success").asBoolean(false);
        } catch (RestClientException e) {
            log.warn("Turnstile siteverify failed; rejecting the request", e);
            return false;
        }
    }
}
