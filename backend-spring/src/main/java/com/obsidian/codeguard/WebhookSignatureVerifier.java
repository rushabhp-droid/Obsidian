package com.obsidian.codeguard;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * GitHub's X-Hub-Signature-256: "sha256=" + hex HMAC-SHA256 of the raw body with the webhook secret.
 * Checked with a constant-time compare before any of the body is parsed.
 */
public final class WebhookSignatureVerifier {

    private final byte[] secret;

    public WebhookSignatureVerifier(String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean verify(byte[] body, String signatureHeader) {
        if (secret.length == 0 || body == null || signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        byte[] expected = ("sha256=" + HexFormat.of().formatHex(hmac(body))).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signatureHeader.getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
