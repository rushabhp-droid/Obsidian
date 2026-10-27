package com.obsidian.codeguard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.auth.Tokens;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoScannerTest {

    @TempDir Path dir;

    RepoScanner scanner(String gitleaks, String osv) {
        return new RepoScanner(new ToolRunner(), new ObjectMapper(), new CodeGuardProperties("1", "k", "s", "a@x.example",
                null, gitleaks, osv, Duration.ofSeconds(30), 1, 1));
    }

    Path source() throws Exception {
        Path src = Files.createDirectories(dir.resolve("src/config"));
        Files.writeString(src.resolve("settings.py"), "import os\n\nAWS_KEY = \"" + FakeTools.SECRET + "\"\n");
        return dir.resolve("src");
    }

    @Test
    void leakedSecretIsFingerprintedNeverKept() throws Exception {
        Path src = source();
        RepoScanner.Outcome outcome = scanner(FakeTools.gitleaks(dir).toString(), FakeTools.osvScanner(dir).toString())
                .scan(src, dir);

        RepoScanner.Found secret = outcome.findings().stream().filter(f -> f.type() == CodeFinding.Type.SECRET)
                .findFirst().orElseThrow();
        assertThat(secret.ruleId()).isEqualTo("aws-access-token");
        assertThat(secret.location()).isEqualTo("config/settings.py:3");
        assertThat(secret.severity()).isEqualTo(CodeFinding.Severity.CRITICAL);
        assertThat(secret.fingerprint()).isEqualTo(Tokens.sha256Hex(FakeTools.SECRET));
        assertThat(secret.toString()).doesNotContain(FakeTools.SECRET);
        assertThat(secret.message()).contains("rotate").doesNotContain("hide");
    }

    @Test
    void vulnerableDependencyIsReportedWithItsAdvisoryAndCvssSeverity() throws Exception {
        RepoScanner.Outcome outcome = scanner(FakeTools.gitleaks(dir).toString(), FakeTools.osvScanner(dir).toString())
                .scan(source(), dir);

        assertThat(outcome.findings()).filteredOn(f -> f.type() == CodeFinding.Type.VULN_DEPENDENCY).singleElement()
                .satisfies(f -> {
                    assertThat(f.ruleId()).isEqualTo("GHSA-p6mc-m468-83gw");
                    assertThat(f.location()).isEqualTo("lodash@4.17.15");
                    assertThat(f.severity()).isEqualTo(CodeFinding.Severity.HIGH);
                    assertThat(f.path()).isEqualTo("package-lock.json");
                });
        assertThat(outcome.unavailableTools()).isEmpty();
    }

    @Test
    void missingScannersAreReportedNotFatal() throws Exception {
        RepoScanner.Outcome outcome = scanner(dir.resolve("no-gitleaks").toString(), dir.resolve("no-osv").toString())
                .scan(source(), dir);

        assertThat(outcome.findings()).isEmpty();
        assertThat(outcome.unavailableTools()).containsExactly("gitleaks", "osv-scanner");
    }

    @Test
    void cvssScoresMapToSeverities() {
        assertThat(RepoScanner.severity("9.8")).isEqualTo(CodeFinding.Severity.CRITICAL);
        assertThat(RepoScanner.severity("7.0")).isEqualTo(CodeFinding.Severity.HIGH);
        assertThat(RepoScanner.severity("5.3")).isEqualTo(CodeFinding.Severity.MEDIUM);
        assertThat(RepoScanner.severity("2.1")).isEqualTo(CodeFinding.Severity.LOW);
        assertThat(RepoScanner.severity("")).isEqualTo(CodeFinding.Severity.MEDIUM);
    }
}
