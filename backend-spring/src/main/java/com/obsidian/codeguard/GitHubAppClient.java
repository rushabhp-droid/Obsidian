package com.obsidian.codeguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

/**
 * The GitHub App side of Code Guard: an RS256 app JWT, installation tokens, repository snapshots
 * (zipball, extracted safely), release asset hashes, and Check Runs. Downloads follow GitHub's
 * redirects without the Authorization header, which must never reach the storage host.
 */
public class GitHubAppClient {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_ENTRIES = 50_000;

    private final CodeGuardProperties config;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final HttpClient http;
    private final String api;
    private volatile PrivateKey key;

    public GitHubAppClient(CodeGuardProperties config, ObjectMapper mapper, Clock clock) {
        this.config = config;
        this.mapper = mapper;
        this.clock = clock;
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10)).build();
        this.api = config.apiUrl() == null || config.apiUrl().isBlank() ? "https://api.github.com"
                : config.apiUrl().replaceAll("/+$", "");
    }

    /** App JWT: iat 60 s in the past for clock drift, valid 9 minutes (GitHub allows 10). */
    String appJwt() {
        long now = clock.instant().getEpochSecond();
        String header = b64(json(Map.of("alg", "RS256", "typ", "JWT")));
        String payload = b64(json(Map.of("iat", now - 60, "exp", now + 540, "iss", config.appId())));
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey());
            signer.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
            return header + "." + payload + "." + b64(signer.sign());
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Signing the GitHub App JWT failed", e);
        }
    }

    public String installationToken(long installationId) {
        JsonNode body = send("POST", api + "/app/installations/" + installationId + "/access_tokens", "Bearer " + appJwt(),
                null);
        return body.path("token").asText();
    }

    /** Extracts the repository at {@code ref} into {@code target}, dropping GitHub's top-level folder. */
    public void downloadSnapshot(String token, String repo, String ref, Path target) {
        Path zip = target.resolveSibling(target.getFileName() + ".zip");
        try {
            try (InputStream in = download(api + "/repos/" + repo + "/zipball/" + ref, token, "application/vnd.github+json")) {
                copyCapped(in, zip, config.maxArchiveBytes());
            }
            extract(zip, target, config.maxArchiveBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                Files.deleteIfExists(zip);
            } catch (IOException ignored) {
                // best effort; the whole temp dir is deleted by the caller
            }
        }
    }

    /** SHA-256 of a release asset, streamed (never stored), capped at maxAssetBytes. */
    public String assetSha256(String token, String assetApiUrl) {
        try (InputStream in = download(assetApiUrl, token, "application/octet-stream")) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int read; (read = in.read(buffer)) > 0; ) {
                total += read;
                if (total > config.maxAssetBytes()) {
                    throw new IllegalStateException("asset is larger than " + config.maxAssetBytes() + " bytes");
                }
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void createCheckRun(String token, String repo, Map<String, Object> checkRun) {
        send("POST", api + "/repos/" + repo + "/check-runs", "Bearer " + token, checkRun);
    }

    // ---- HTTP -----------------------------------------------------------------------------------

    private JsonNode send(String method, String url, String authorization, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("Authorization", authorization).header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (body != null) {
                request.header("Content-Type", "application/json");
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("GitHub " + method + " " + URI.create(url).getPath() + " answered "
                        + response.statusCode());
            }
            return response.body().isEmpty() ? mapper.createObjectNode() : mapper.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** GET with the token on the GitHub API request only; redirects (codeload, S3) are followed without it. */
    private InputStream download(String url, String token, String accept) throws IOException {
        URI uri = URI.create(url);
        boolean authorized = true;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).header("Accept", accept);
                if (authorized) {
                    request.header("Authorization", "Bearer " + token).header("X-GitHub-Api-Version", "2022-11-28");
                }
                HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status / 100 == 3) {
                    response.body().close();
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new IOException("redirect without Location"));
                    uri = uri.resolve(location);
                    authorized = false;
                    continue;
                }
                if (status / 100 != 2) {
                    response.body().close();
                    throw new IOException("download of " + uri.getPath() + " answered " + status);
                }
                return response.body();
            }
            throw new IOException("too many redirects for " + url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static void copyCapped(InputStream in, Path file, long max) throws IOException {
        Path temp = Files.createTempFile(file.getParent(), "dl", ".part");
        try (var out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int read; (read = in.read(buffer)) > 0; ) {
                total += read;
                if (total > max) {
                    throw new IOException("download is larger than " + max + " bytes");
                }
                out.write(buffer, 0, read);
            }
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Zip-slip safe: every entry must stay inside target; total size and entry count are capped. */
    static void extract(Path zip, Path target, long maxBytes) throws IOException {
        Files.createDirectories(target);
        Path root = target.toRealPath();
        long total = 0;
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("archive has more than " + MAX_ENTRIES + " entries");
                }
                String name = entry.getName();
                int slash = name.indexOf('/');
                String relative = slash < 0 ? "" : name.substring(slash + 1); // drop "owner-repo-sha/"
                if (relative.isEmpty()) {
                    continue;
                }
                Path destination = root.resolve(relative).normalize();
                if (!destination.startsWith(root) || relative.startsWith("/") || relative.contains("\\")) {
                    throw new IOException("archive entry escapes the target directory: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                try (var out = Files.newOutputStream(destination)) {
                    byte[] buffer = new byte[64 * 1024];
                    for (int read; (read = in.read(buffer)) > 0; ) {
                        total += read;
                        if (total > maxBytes) {
                            throw new IOException("archive expands to more than " + maxBytes + " bytes");
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    // ---- JWT helpers ----------------------------------------------------------------------------

    private PrivateKey privateKey() {
        PrivateKey cached = key;
        if (cached != null) {
            return cached;
        }
        try (Reader reader = Files.newBufferedReader(Path.of(config.privateKeyPath()));
                PEMParser parser = new PEMParser(reader)) {
            Object pem = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            PrivateKey parsed = switch (pem) {
                case PEMKeyPair pair -> converter.getKeyPair(pair).getPrivate();      // "BEGIN RSA PRIVATE KEY" (GitHub's format)
                case PrivateKeyInfo info -> converter.getPrivateKey(info);          // "BEGIN PRIVATE KEY"
                case null, default -> throw new IllegalStateException("GITHUB_APP_PRIVATE_KEY_PATH isn't a PEM private key");
            };
            key = parsed;
            return parsed;
        } catch (IOException e) {
            throw new UncheckedIOException("Reading the GitHub App private key failed", e);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String b64(String value) {
        return b64(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
