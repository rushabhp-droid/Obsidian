package com.obsidian.scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.obsidian.config.ObsidianProperties;
import com.obsidian.model.ThreatCategory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * VirusTotal API v3, behind the Redis cache and the client-side limiter.
 * Anything not known yet (unknown URL submitted for analysis, analysis still
 * running, quota used up, no API key, VirusTotal unreachable) is PENDING,
 * never CLEAN. The only VirusTotal client: Code Guard [P1] must reuse it.
 */
@Component
public class VirusTotalClient {

    private static final Logger log = LoggerFactory.getLogger(VirusTotalClient.class);

    private final ObsidianProperties.VirusTotal config;
    private final RestClient http;
    private final VtCache cache;
    private final VtRateLimiter limiter;
    private final Clock clock;

    public VirusTotalClient(ObsidianProperties properties, RestClient.Builder builder, VtCache cache,
            VtRateLimiter limiter, Clock clock) {
        this.config = properties.virustotal();
        this.http = builder.baseUrl(config.baseUrl())
                .defaultHeader("x-apikey", config.apiKey() == null ? "" : config.apiKey())
                .build();
        this.cache = cache;
        this.limiter = limiter;
        this.clock = clock;
    }

    /** VirusTotal's URL identifier: the unpadded base64url of the URL exactly as scanned. */
    public static String urlId(String url) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(url.getBytes(StandardCharsets.UTF_8));
    }

    public VtVerdict lookupUrl(String url) {
        String id = urlId(url);
        Optional<VtVerdict> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }
        if (!config.enabled()) {
            return VtVerdict.pending();
        }
        Optional<String> analysis = cache.pendingAnalysis(id);
        if (analysis.isPresent()) {
            return poll(id, analysis.get());
        }
        if (!limiter.tryAcquire()) {
            return VtVerdict.pending();
        }
        try {
            JsonNode report = http.get().uri("/urls/{id}", id).retrieve().body(JsonNode.class);
            return remember(id, fromStats(report.path("data").path("attributes").path("last_analysis_stats"),
                    ThreatCategory.PHISHING));
        } catch (HttpClientErrorException.NotFound e) {
            return submit(id, url);
        } catch (RestClientException e) {
            return failed("GET /urls", e);
        }
    }

    public VtVerdict lookupFile(String sha256) {
        Optional<VtVerdict> cached = cache.get(sha256);
        if (cached.isPresent()) {
            return cached.get();
        }
        if (!config.enabled() || cache.isUnknownFile(sha256) || !limiter.tryAcquire()) {
            return VtVerdict.pending();
        }
        try {
            JsonNode report = http.get().uri("/files/{sha256}", sha256).retrieve().body(JsonNode.class);
            return remember(sha256, fromStats(report.path("data").path("attributes").path("last_analysis_stats"),
                    ThreatCategory.MALWARE));
        } catch (HttpClientErrorException.NotFound e) {
            cache.markUnknownFile(sha256); // attachments are never uploaded, so this stays PENDING
            return VtVerdict.pending();
        } catch (RestClientException e) {
            return failed("GET /files", e);
        }
    }

    private VtVerdict submit(String id, String url) {
        if (!limiter.tryAcquire()) {
            return VtVerdict.pending();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("url", url);
        try {
            JsonNode submitted = http.post().uri("/urls").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
            String analysisId = submitted.path("data").path("id").asText("");
            if (!analysisId.isEmpty()) {
                cache.markPending(id, analysisId);
            }
            return VtVerdict.pending();
        } catch (RestClientException e) {
            return failed("POST /urls", e);
        }
    }

    private VtVerdict poll(String id, String analysisId) {
        if (!limiter.tryAcquire()) {
            return VtVerdict.pending();
        }
        try {
            JsonNode analysis = http.get().uri("/analyses/{id}", analysisId).retrieve().body(JsonNode.class);
            JsonNode attributes = analysis.path("data").path("attributes");
            if (!"completed".equals(attributes.path("status").asText())) {
                return VtVerdict.pending();
            }
            cache.clearPending(id);
            return remember(id, fromStats(attributes.path("stats"), ThreatCategory.PHISHING));
        } catch (HttpClientErrorException.NotFound e) {
            cache.clearPending(id); // analysis expired: the next lookup starts over
            return VtVerdict.pending();
        } catch (RestClientException e) {
            return failed("GET /analyses", e);
        }
    }

    private VtVerdict remember(String id, VtVerdict verdict) {
        cache.put(id, verdict);
        return verdict;
    }

    private VtVerdict fromStats(JsonNode stats, ThreatCategory whenFlagged) {
        return VtVerdict.fromStats(stats.path("malicious").asInt(0), stats.path("suspicious").asInt(0), whenFlagged,
                clock.instant());
    }

    private VtVerdict failed(String call, RestClientException e) {
        if (e instanceof HttpClientErrorException.TooManyRequests) {
            limiter.backOff();
            log.warn("VirusTotal quota exceeded on {}; backing off for a minute", call);
        } else {
            log.warn("VirusTotal {} failed: {}", call, e.getMessage());
        }
        return VtVerdict.pending();
    }
}
