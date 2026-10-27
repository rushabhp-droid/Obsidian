package com.obsidian.codeguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A stand-in for api.github.com with GitHub's real shapes: app-JWT-authenticated installation tokens,
 * zipball and release-asset downloads that redirect to another host, and Check Runs.
 */
final class FakeGitHub implements AutoCloseable {

    final HttpServer server;
    final KeyPair appKey;
    final List<JsonNode> checkRuns = new CopyOnWriteArrayList<>();
    final List<String> authorizationOnRedirectTargets = new CopyOnWriteArrayList<>();
    final Map<String, byte[]> zipballs = new ConcurrentHashMap<>();
    final Map<String, byte[]> assets = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    FakeGitHub() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        appKey = generator.generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** The App's private key as GitHub hands it out: PKCS#1 "BEGIN RSA PRIVATE KEY". */
    Path writePkcs1Key(Path dir) throws Exception {
        var info = org.bouncycastle.asn1.pkcs.PrivateKeyInfo.getInstance(appKey.getPrivate().getEncoded());
        byte[] pkcs1 = info.parsePrivateKey().toASN1Primitive().getEncoded();
        return write(dir.resolve("app.pem"), "RSA PRIVATE KEY", pkcs1);
    }

    Path writePkcs8Key(Path dir) throws Exception {
        return write(dir.resolve("app-pkcs8.pem"), "PRIVATE KEY", appKey.getPrivate().getEncoded());
    }

    private static Path write(Path file, String type, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        Files.writeString(file, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
        return file;
    }

    static byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        try {
            if (path.matches("/app/installations/\\d+/access_tokens") && "POST".equals(exchange.getRequestMethod())) {
                if (auth == null || !auth.startsWith("Bearer ") || !validJwt(auth.substring(7))) {
                    respond(exchange, 401, "{\"message\":\"A JSON web token could not be decoded\"}");
                    return;
                }
                respond(exchange, 201, "{\"token\":\"ghs_installation_token\",\"expires_at\":\"2030-01-01T00:00:00Z\"}");
            } else if (path.matches("/repos/[^/]+/[^/]+/zipball/.+")) {
                requireToken(auth);
                exchange.getResponseHeaders().add("Location", "/codeload/" + path.substring(path.lastIndexOf('/') + 1));
                respond(exchange, 302, "");
            } else if (path.startsWith("/codeload/") || path.startsWith("/objects/")) {
                authorizationOnRedirectTargets.add(String.valueOf(auth));
                String key = path.substring(path.lastIndexOf('/') + 1);
                byte[] body = path.startsWith("/codeload/") ? zipballs.get(key) : assets.get(key);
                exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : body.length);
                if (body != null) {
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            } else if (path.matches("/repos/[^/]+/[^/]+/releases/assets/.+")) {
                requireToken(auth);
                exchange.getResponseHeaders().add("Location", "/objects/" + path.substring(path.lastIndexOf('/') + 1));
                respond(exchange, 302, "");
            } else if (path.matches("/repos/[^/]+/[^/]+/check-runs")) {
                requireToken(auth);
                checkRuns.add(mapper.readTree(exchange.getRequestBody()));
                respond(exchange, 201, "{\"id\":1}");
            } else {
                respond(exchange, 404, "{\"message\":\"Not Found\"}");
            }
        } catch (SecurityException e) {
            respond(exchange, 401, "{\"message\":\"Bad credentials\"}");
        }
    }

    private static void requireToken(String auth) {
        if (!"Bearer ghs_installation_token".equals(auth)) {
            throw new SecurityException();
        }
    }

    boolean validJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify((RSAPublicKey) appKey.getPublic());
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long now = System.currentTimeMillis() / 1000;
            return verifier.verify(Base64.getUrlDecoder().decode(parts[2])) && "12345".equals(claims.path("iss").asText())
                    && claims.path("iat").asLong() <= now && claims.path("exp").asLong() > now
                    && claims.path("exp").asLong() - claims.path("iat").asLong() <= 600;
        } catch (Exception e) {
            return false;
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
