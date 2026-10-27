package com.obsidian.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.obsidian.scanner.MimeEmailParser.ParsedEmail;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MimeEmailParserTest {

    static byte[] fixture(String name) throws IOException {
        try (var in = MimeEmailParserTest.class.getResourceAsStream("/emails/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void readsHeadersLinksAndAttachmentHashes() throws IOException {
        ParsedEmail email = MimeEmailParser.parse(fixture("phishing.eml"));

        assertThat(email.from()).isEqualTo("Payroll Team <Payroll@Aster-Payroll.example>");
        assertThat(email.senderAddress()).isEqualTo("payroll@aster-payroll.example");
        assertThat(email.subject()).isEqualTo("Action required: confirm your salary account");
        assertThat(email.receivedAt()).isEqualTo(Instant.parse("2026-10-27T08:10:00Z"));
        assertThat(email.authenticationResults()).hasSize(1).first().asString().startsWith("mx.google.com;");
        assertThat(email.links()).containsExactly("http://login-verify.aster-payroll.example/reset",
                "https://Docs.Example.org/Help");
        assertThat(email.attachments()).singleElement().satisfies(file -> {
            assertThat(file.filename()).isEqualTo("invoice-4471.pdf.exe");
            assertThat(file.sha256()).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
        });
    }

    @Test
    void keepsAuthenticationResultsInHeaderOrder() throws IOException {
        ParsedEmail email = MimeEmailParser.parse(fixture("official.eml"));

        assertThat(email.authenticationResults()).hasSize(2);
        assertThat(email.authenticationResults().get(0)).startsWith("mx.google.com;");
        assertThat(email.authenticationResults().get(1)).startsWith("evil.example;");
        assertThat(email.links()).containsExactly("https://github.com/settings/keys");
        assertThat(email.attachments()).isEmpty();
    }
}
