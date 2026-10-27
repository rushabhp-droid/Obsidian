package com.obsidian.codeguard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

    // GitHub's documented example: secret "It's a Secret to Everybody", payload "Hello, World!".
    static final byte[] BODY = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    static final String SIGNATURE = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";
    final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("It's a Secret to Everybody");

    @Test
    void acceptsGitHubsDocumentedSignature() {
        assertThat(verifier.verify(BODY, SIGNATURE)).isTrue();
    }

    @Test
    void rejectsATamperedBodyOrSignature() {
        assertThat(verifier.verify("Hello, World?".getBytes(StandardCharsets.UTF_8), SIGNATURE)).isFalse();
        assertThat(verifier.verify(BODY, SIGNATURE.replace('7', '8'))).isFalse();
        assertThat(verifier.verify(BODY, SIGNATURE.substring(0, 20))).isFalse();
    }

    @Test
    void rejectsMissingLegacySha1AndUnconfiguredSecrets() {
        assertThat(verifier.verify(BODY, null)).isFalse();
        assertThat(verifier.verify(BODY, "sha1=01dc10d0c83e72ed246219cdd91669667fe2ca59")).isFalse();
        assertThat(new WebhookSignatureVerifier("").verify(BODY, SIGNATURE)).isFalse();
    }
}
