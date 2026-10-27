package com.obsidian.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.obsidian.config.ObsidianProperties;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Google OAuth 2.0 and the Gmail REST API (users.messages list/get, users.getProfile). */
@Component
public class GmailClient {

    static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    static final String GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me";
    static final String UNREAD = "is:unread (in:inbox OR in:spam)";

    public record Tokens(String accessToken, String refreshToken) {}

    private final ObsidianProperties.Gmail config;
    private final RestClient http;

    public GmailClient(ObsidianProperties properties, RestClient.Builder builder) {
        this.config = properties.gmail();
        this.http = builder.build();
    }

    /** Consent URL: offline access so Google returns a refresh token, prompt=consent so it always does. */
    public static String authorizationUrl(ObsidianProperties.Gmail config, String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", config.scopes())
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .encode().build().toUriString();
    }

    public Tokens exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = credentials();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        JsonNode body = postForm(TOKEN_URL, form);
        return new Tokens(body.path("access_token").asText(), body.path("refresh_token").asText(null));
    }

    public String refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = credentials();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postForm(TOKEN_URL, form).path("access_token").asText();
    }

    public String profileAddress(String accessToken) {
        return get(accessToken, GMAIL + "/profile").path("emailAddress").asText();
    }

    public List<String> unreadMessageIds(String accessToken, int max) {
        JsonNode body = http.get().uri(GMAIL + "/messages?q={q}&maxResults={max}&includeSpamTrash=true", UNREAD, max)
                .header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class);
        List<String> ids = new ArrayList<>();
        body.path("messages").forEach(message -> ids.add(message.path("id").asText()));
        return ids;
    }

    /** The full RFC 822 message (format=raw), decoded from base64url. */
    public byte[] rawMessage(String accessToken, String messageId) {
        String raw = http.get().uri(GMAIL + "/messages/{id}?format=raw", messageId)
                .header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class).path("raw").asText();
        return Base64.getUrlDecoder().decode(raw);
    }

    public void revoke(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        http.post().uri(REVOKE_URL).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
    }

    private MultiValueMap<String, String> credentials() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        return form;
    }

    private JsonNode postForm(String url, MultiValueMap<String, String> form) {
        return http.post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
                .body(JsonNode.class);
    }

    private JsonNode get(String accessToken, String url) {
        return http.get().uri(url).header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class);
    }
}
