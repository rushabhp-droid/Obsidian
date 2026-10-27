package com.obsidian.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.obsidian.config.ObsidianProperties;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.Verdict;
import com.obsidian.support.IntegrationTest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** VirusTotal v3 is stubbed with its real response shapes; the cache is the real Redis. */
class VirusTotalClientTest extends IntegrationTest {

    static final String VT = "https://www.virustotal.com/api/v3";

    @Autowired VtCache cache;
    @Autowired StringRedisTemplate redis;

    MockRestServiceServer virusTotal;
    VirusTotalClient client;

    static ObsidianProperties properties(String apiKey) {
        return new ObsidianProperties("http://localhost:3000", List.of(), "", null, null,
                new ObsidianProperties.VirusTotal(apiKey, VT, 4, 500), null, null);
    }

    VirusTotalClient client(String apiKey, VtRateLimiter limiter) {
        RestClient.Builder builder = RestClient.builder();
        virusTotal = MockRestServiceServer.bindTo(builder).build();
        return new VirusTotalClient(properties(apiKey), builder, cache, limiter, Clock.systemUTC());
    }

    @BeforeEach
    void setUp() {
        client = client("vt-key", new VtRateLimiter(4, 500, Clock.systemUTC()));
    }

    static String unique(String url) {
        return url + "?t=" + UUID.randomUUID();
    }

    static String urlReport(int malicious, int suspicious) {
        return """
                {"data":{"type":"url","id":"x","attributes":{"url":"http://x","last_analysis_date":1730000000,
                "reputation":-50,"last_analysis_stats":{"harmless":60,"malicious":%d,"suspicious":%d,
                "undetected":20,"timeout":0}}}}""".formatted(malicious, suspicious);
    }

    static final String NOT_FOUND = "{\"error\":{\"code\":\"NotFoundError\",\"message\":\"URL not found\"}}";

    @Test
    void urlIdIsTheUnpaddedBase64UrlOfTheUrl() {
        assertThat(VirusTotalClient.urlId("http://testsafebrowsing.appspot.com/s/phishing.html"))
                .isEqualTo("aHR0cDovL3Rlc3RzYWZlYnJvd3NpbmcuYXBwc3BvdC5jb20vcy9waGlzaGluZy5odG1s");
        assertThat(VirusTotalClient.urlId("https://login-verify.example/reset"))
                .isEqualTo("aHR0cHM6Ly9sb2dpbi12ZXJpZnkuZXhhbXBsZS9yZXNldA");
    }

    @Test
    void knownUrlIsJudgedByItsLastAnalysisAndCachedForADay() {
        String url = unique("http://testsafebrowsing.appspot.com/s/phishing.html");
        String id = VirusTotalClient.urlId(url);
        virusTotal.expect(requestTo(VT + "/urls/" + id)).andExpect(method(HttpMethod.GET))
                .andExpect(header("x-apikey", "vt-key"))
                .andRespond(withSuccess(urlReport(9, 1), MediaType.APPLICATION_JSON));

        VtVerdict verdict = client.lookupUrl(url);

        assertThat(verdict.verdict()).isEqualTo(Verdict.MALICIOUS);
        assertThat(verdict.malicious()).isEqualTo(9);
        assertThat(verdict.category()).isEqualTo(ThreatCategory.PHISHING);
        assertThat(redis.getExpire("vt:cache:" + id)).isCloseTo(86_400L, within(5L));
        virusTotal.verify();
    }

    @Test
    void cachedVerdictNeverCallsVirusTotal() {
        String url = unique("https://login-verify.example/reset");
        redis.opsForValue().set("vt:cache:" + VirusTotalClient.urlId(url),
                "{\"verdict\":\"SUSPICIOUS\",\"malicious\":2,\"suspicious\":0,\"category\":\"PHISHING\",\"checkedAt\":\"2026-10-27T10:00:00Z\"}");

        VtVerdict verdict = client.lookupUrl(url);

        assertThat(verdict.verdict()).isEqualTo(Verdict.SUSPICIOUS);
        assertThat(verdict.malicious()).isEqualTo(2);
        virusTotal.verify();
    }

    @Test
    void unknownUrlIsSubmittedAndReportedPending() {
        String url = unique("https://new-phish.example/login");
        String id = VirusTotalClient.urlId(url);
        virusTotal.expect(requestTo(VT + "/urls/" + id))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(NOT_FOUND));
        virusTotal.expect(requestTo(VT + "/urls")).andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("url", url)))
                .andRespond(withSuccess("{\"data\":{\"type\":\"analysis\",\"id\":\"u-abc-1730000000\","
                        + "\"links\":{\"self\":\"" + VT + "/analyses/u-abc-1730000000\"}}}", MediaType.APPLICATION_JSON));

        VtVerdict verdict = client.lookupUrl(url);

        assertThat(verdict.verdict()).isEqualTo(Verdict.PENDING);
        assertThat(redis.opsForValue().get("vt:pending:" + id)).isEqualTo("u-abc-1730000000");
        assertThat(redis.hasKey("vt:cache:" + id)).isFalse();
        virusTotal.verify();
    }

    @Test
    void submittedUrlIsPolledUntilTheAnalysisCompletes() {
        String url = unique("https://new-phish.example/login");
        String id = VirusTotalClient.urlId(url);
        redis.opsForValue().set("vt:pending:" + id, "u-abc-1730000000");
        virusTotal.expect(requestTo(VT + "/analyses/u-abc-1730000000"))
                .andRespond(withSuccess("""
                        {"data":{"type":"analysis","id":"u-abc-1730000000","attributes":{"status":"queued",
                        "stats":{"harmless":0,"malicious":0,"suspicious":0,"undetected":0,"timeout":0}}}}""",
                        MediaType.APPLICATION_JSON));
        virusTotal.expect(requestTo(VT + "/analyses/u-abc-1730000000"))
                .andRespond(withSuccess("""
                        {"data":{"type":"analysis","id":"u-abc-1730000000","attributes":{"status":"completed",
                        "date":1730000100,"stats":{"harmless":50,"malicious":4,"suspicious":0,"undetected":30,
                        "timeout":0}}}}""", MediaType.APPLICATION_JSON));

        assertThat(client.lookupUrl(url).verdict()).isEqualTo(Verdict.PENDING);
        VtVerdict done = client.lookupUrl(url);

        assertThat(done.verdict()).isEqualTo(Verdict.MALICIOUS);
        assertThat(done.malicious()).isEqualTo(4);
        assertThat(redis.hasKey("vt:pending:" + id)).isFalse();
        assertThat(redis.hasKey("vt:cache:" + id)).isTrue();
        virusTotal.verify();
    }

    @Test
    void exhaustedQuotaIsPendingWithoutCallingVirusTotal() {
        VirusTotalClient noQuota = client("vt-key", new VtRateLimiter(0, 500, Clock.systemUTC()));

        assertThat(noQuota.lookupUrl(unique("https://quota.example/x")).verdict()).isEqualTo(Verdict.PENDING);
        assertThat(noQuota.lookupFile("1".repeat(64)).verdict()).isEqualTo(Verdict.PENDING);
        virusTotal.verify();
    }

    @Test
    void quotaErrorFromVirusTotalIsPendingAndBacksOff() {
        String url = unique("https://quota.example/x");
        virusTotal.expect(requestTo(VT + "/urls/" + VirusTotalClient.urlId(url)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"QuotaExceededError\",\"message\":\"Quota exceeded\"}}"));

        assertThat(client.lookupUrl(url).verdict()).isEqualTo(Verdict.PENDING);
        assertThat(client.lookupUrl(unique("https://quota.example/y")).verdict()).isEqualTo(Verdict.PENDING);
        virusTotal.verify();
    }

    @Test
    void knownFileHashIsMalware() {
        String sha = UUID.randomUUID().toString().replace("-", "") + "0".repeat(32);
        virusTotal.expect(requestTo(VT + "/files/" + sha)).andExpect(header("x-apikey", "vt-key"))
                .andRespond(withSuccess("""
                        {"data":{"type":"file","id":"%s","attributes":{"meaningful_name":"invoice.pdf.exe",
                        "last_analysis_stats":{"harmless":0,"type-unsupported":5,"suspicious":0,
                        "confirmed-timeout":0,"timeout":0,"failure":0,"malicious":31,"undetected":30}}}}"""
                        .formatted(sha), MediaType.APPLICATION_JSON));

        VtVerdict verdict = client.lookupFile(sha);

        assertThat(verdict.verdict()).isEqualTo(Verdict.MALICIOUS);
        assertThat(verdict.malicious()).isEqualTo(31);
        assertThat(verdict.category()).isEqualTo(ThreatCategory.MALWARE);
    }

    @Test
    void unknownFileHashStaysPendingAndIsNotAskedAgainSoon() {
        String sha = UUID.randomUUID().toString().replace("-", "") + "1".repeat(32);
        virusTotal.expect(requestTo(VT + "/files/" + sha))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(NOT_FOUND));

        assertThat(client.lookupFile(sha).verdict()).isEqualTo(Verdict.PENDING);
        assertThat(client.lookupFile(sha).verdict()).isEqualTo(Verdict.PENDING);
        virusTotal.verify();
    }

    @Test
    void withoutAnApiKeyEverythingUnknownIsPending() {
        VirusTotalClient disabled = client("", new VtRateLimiter(4, 500, Clock.systemUTC()));

        assertThat(disabled.lookupUrl(unique("https://no-key.example/")).verdict()).isEqualTo(Verdict.PENDING);
        virusTotal.verify();
    }
}
