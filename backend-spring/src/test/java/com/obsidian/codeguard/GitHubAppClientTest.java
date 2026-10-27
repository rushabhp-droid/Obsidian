package com.obsidian.codeguard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubAppClientTest {

    @TempDir Path dir;
    FakeGitHub github;

    GitHubAppClient client(Path key, long maxAssetBytes) throws Exception {
        github = github == null ? new FakeGitHub() : github;
        return new GitHubAppClient(new CodeGuardProperties("12345", key.toString(), "secret", "admin@x.example",
                github.url(), "gitleaks", "osv-scanner", Duration.ofSeconds(30), 1_000_000, maxAssetBytes),
                new ObjectMapper(), Clock.systemUTC());
    }

    @AfterEach
    void stop() {
        if (github != null) {
            github.close();
        }
    }

    @Test
    void appJwtFromGitHubsPkcs1KeyGetsAnInstallationToken() throws Exception {
        github = new FakeGitHub();
        GitHubAppClient client = client(github.writePkcs1Key(dir), 1000);

        assertThat(client.installationToken(42)).isEqualTo("ghs_installation_token");
    }

    @Test
    void pkcs8KeysWorkToo() throws Exception {
        github = new FakeGitHub();
        GitHubAppClient client = client(github.writePkcs8Key(dir), 1000);

        assertThat(github.validJwt(client.appJwt())).isTrue();
    }

    @Test
    void snapshotIsExtractedWithoutGitHubsTopFolderAndTheTokenNeverFollowsTheRedirect() throws Exception {
        github = new FakeGitHub();
        GitHubAppClient client = client(github.writePkcs1Key(dir), 1000);
        github.zipballs.put("abc123", FakeGitHub.zip(Map.of("acme-api-abc123/", "", "acme-api-abc123/src/app.py", "print(1)\n")));

        client.downloadSnapshot("ghs_installation_token", "acme/api", "abc123", dir.resolve("src"));

        assertThat(Files.readString(dir.resolve("src/src/app.py"))).isEqualTo("print(1)\n");
        assertThat(github.authorizationOnRedirectTargets).containsExactly("null");
    }

    @Test
    void zipEntriesThatEscapeTheTargetAreRefused() throws Exception {
        Path zip = dir.resolve("evil.zip");
        Files.write(zip, FakeGitHub.zip(Map.of("acme-api-abc/../../outside.txt", "pwned")));

        assertThatThrownBy(() -> GitHubAppClient.extract(zip, dir.resolve("target"), 1_000_000))
                .hasMessageContaining("escapes");
        assertThat(Files.exists(dir.resolve("outside.txt"))).isFalse();
    }

    @Test
    void zipBombsAreCappedBySize() throws Exception {
        Path zip = dir.resolve("big.zip");
        Files.write(zip, FakeGitHub.zip(Map.of("r/big.txt", "x".repeat(5000))));

        assertThatThrownBy(() -> GitHubAppClient.extract(zip, dir.resolve("target"), 1000)).hasMessageContaining("expands");
    }

    @Test
    void releaseAssetsAreHashedWhileStreamingAndCapped() throws Exception {
        github = new FakeGitHub();
        GitHubAppClient client = client(github.writePkcs1Key(dir), 1000);
        byte[] binary = "MZ fake release binary".getBytes(StandardCharsets.UTF_8);
        github.assets.put("7", binary);
        github.assets.put("8", new byte[2000]);
        String asset = github.url() + "/repos/acme/api/releases/assets/";

        assertThat(client.assetSha256("ghs_installation_token", asset + "7"))
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(binary)));
        assertThatThrownBy(() -> client.assetSha256("ghs_installation_token", asset + "8")).hasMessageContaining("larger");
        assertThat(github.authorizationOnRedirectTargets).allMatch("null"::equals);
    }
}
