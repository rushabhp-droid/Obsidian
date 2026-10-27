package com.obsidian.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.obsidian.config.ObsidianProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TurnstileVerifierTest {

    static final String URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    static ObsidianProperties properties(String secret) {
        return new ObsidianProperties("http://localhost:3000", List.of(), "", null,
                new ObsidianProperties.Turnstile(secret, URL), null, null, null);
    }

    @Test
    void postsTheSecretAndTokenAndTrustsOnlySuccess() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer cloudflare = MockRestServiceServer.bindTo(builder).build();
        cloudflare.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("secret", "s3cret", "response", "token-1")))
                .andRespond(withSuccess("{\"success\":true,\"error-codes\":[]}", MediaType.APPLICATION_JSON));
        cloudflare.expect(requestTo(URL))
                .andRespond(withSuccess("{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}",
                        MediaType.APPLICATION_JSON));
        TurnstileVerifier verifier = new TurnstileVerifier(properties("s3cret"), builder);

        assertThat(verifier.verify("token-1")).isTrue();
        assertThat(verifier.verify("token-1")).isFalse();
        cloudflare.verify();
    }

    @Test
    void cloudflareErrorsFailClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer cloudflare = MockRestServiceServer.bindTo(builder).build();
        cloudflare.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(new TurnstileVerifier(properties("s3cret"), builder).verify("token")).isFalse();
    }

    @Test
    void missingTokenFailsWithoutCallingCloudflare() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer cloudflare = MockRestServiceServer.bindTo(builder).build();

        assertThat(new TurnstileVerifier(properties("s3cret"), builder).verify(null)).isFalse();
        assertThat(new TurnstileVerifier(properties("s3cret"), builder).verify(" ")).isFalse();
        cloudflare.verify();
    }

    @Test
    void blankSecretTurnsVerificationOff() {
        TurnstileVerifier verifier = new TurnstileVerifier(properties(""), RestClient.builder());

        assertThat(verifier.enabled()).isFalse();
        assertThat(verifier.verify(null)).isTrue();
    }
}
