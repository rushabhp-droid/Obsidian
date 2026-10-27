package com.obsidian.b2c;

import com.obsidian.model.IndicatorType;
import com.obsidian.model.ListType;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.Verdict;
import com.obsidian.scanner.ScanResponse.IndicatorResult;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of /api/b2c/v1 (docs/protocol.md, "B2C app API"). */
public final class B2cDtos {

    private B2cDtos() {}

    // ---- auth -------------------------------------------------------------------------------------

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 10, max = 128) String password) {}

    public record VerifyEmailRequest(@NotBlank @Email String email, @NotBlank @Size(max = 12) String code) {}

    public record EmailRequest(@NotBlank @Email String email) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 128) String password) {}

    public record GoogleRequest(@NotBlank @Size(max = 4096) String idToken) {}

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 12) String code,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {}

    /** 202 after register: a code is on its way to {@code email}. */
    public record CodeSent(String email, long expiresInSeconds) {}

    public record SignedIn(String token, Instant expiresAt, Profile consumer) {}

    public record Profile(UUID id, String email, String displayName, boolean emailVerified, boolean hasPassword,
            boolean googleLinked, Instant createdAt) {

        public static Profile of(Consumer c) {
            return new Profile(c.getId(), c.getEmail(), c.getDisplayName(), c.isEmailVerified(),
                    c.getPasswordHash() != null, c.getGoogleSubject() != null, c.getCreatedAt());
        }
    }

    /** GET /status: what the app's connectivity card shows. No sign-in needed. */
    public record Status(String server, String database, String cache, boolean virusTotalConfigured,
            boolean googleSignInConfigured, String googleClientId, boolean emailConfigured, Instant serverTime) {}

    // ---- scanning ---------------------------------------------------------------------------------

    /** {@code source}: where the URL came from; LINK_OPENED (browser defense) or URL_SCAN (Home analyzer). */
    public record UrlScanRequest(@NotBlank @Size(max = 2048) String url, ConsumerActivity.Kind source) {}

    /** The app hashes the file itself; the file never leaves the phone. */
    public record FileScanRequest(
            @NotBlank @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "must be a SHA-256 hash") String sha256,
            @Size(max = 255) String fileName,
            Long sizeBytes) {}

    /**
     * A message notification. {@code app} is the package that posted it. Saved
     * contacts send URLs only; text is allowed only for unknown senders.
     */
    public record MessageScanRequest(
            @NotBlank @Size(max = 255) String app,
            @Size(max = 255) String sender,
            boolean savedContact,
            @Size(max = 5000) String text,
            @Size(max = 20) List<@NotBlank @Size(max = 2048) String> urls) {}

    /**
     * {@code riskScore} 0–100, null while PENDING. {@code trusted}: the consumer's own
     * whitelist decided. {@code decidedBy}: PERSONAL_RULE, SHARED_RULE, VIRUSTOTAL or NONE.
     */
    public record ScanResult(Verdict verdict, ThreatCategory category, Integer riskScore, int vtDetections,
            boolean trusted, String decidedBy, List<IndicatorResult> indicators, UUID activityId) {}

    // ---- activity, rules, reports -----------------------------------------------------------------

    public record Activity(UUID id, ConsumerActivity.Kind kind, String subject, String detail, Verdict verdict,
            ThreatCategory threatCategory, Integer riskScore, int vtDetections, Instant createdAt) {

        public static Activity of(ConsumerActivity a) {
            return new Activity(a.getId(), a.getKind(), a.getSubject(), a.getDetail(), a.getVerdict(),
                    a.getThreatCategory(), a.getRiskScore(), a.getVtDetections(), a.getCreatedAt());
        }
    }

    public record RuleRequest(@NotNull IndicatorType indicatorType, @NotBlank @Size(max = 2048) String indicatorValue,
            @NotNull ListType listType) {}

    public record Rule(UUID id, IndicatorType indicatorType, String indicatorValue, ListType listType,
            Instant createdAt) {

        public static Rule of(ConsumerRule r) {
            return new Rule(r.getId(), r.getIndicatorType(), r.getIndicatorValue(), r.getListType(), r.getCreatedAt());
        }
    }

    /** What the phone firewall enforces: the shared (global) blocklist plus the consumer's own rules. */
    public record Blocklist(List<String> domains, List<String> ips, List<Rule> personal, Instant generatedAt) {}

    public record ReportRequest(
            @NotNull IndicatorType indicatorType,
            @NotBlank @Size(max = 2048) String indicatorValue,
            ThreatCategory threatCategory,
            @Size(max = 500) String note) {}

    /**
     * {@code reporters}: verified consumers who reported it in the window;
     * {@code reportersNeeded}: how many it takes without VirusTotal or an admin.
     */
    public record Report(UUID id, IndicatorType indicatorType, String indicatorValue, ThreatCategory threatCategory,
            String note, ConsumerReport.Status status, long reporters, int reportersNeeded, Instant createdAt) {}
}
