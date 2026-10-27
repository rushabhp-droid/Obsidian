package com.obsidian.codeguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.obsidian.auth.AccountService;
import com.obsidian.firewall.EventEnvelope.Kind;
import com.obsidian.firewall.EventPublisher;
import com.obsidian.model.Account;
import com.obsidian.model.UserRepository;
import com.obsidian.model.Verdict;
import com.obsidian.scanner.VirusTotalClient;
import com.obsidian.scanner.VtVerdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A push or pull request snapshot is scanned with gitleaks
 * and OSV-Scanner in a fresh temp dir (deleted afterwards) and gets a Check Run; a published release
 * has each asset's SHA-256 resolved through the shared VirusTotalClient (same cache, limiter, PENDING).
 * Findings land in code_findings and on the dashboard live feed.
 */
public class CodeGuardService {

    static final String CHECK_NAME = "Obsidian Code Guard";
    private static final Logger log = LoggerFactory.getLogger(CodeGuardService.class);
    private static final int MAX_ANNOTATIONS = 50;

    private final GitHubAppClient github;
    private final RepoScanner scanner;
    private final VirusTotalClient virusTotal;
    private final CodeFindingRepository findings;
    private final EventPublisher events;
    private final UserRepository users;
    private final AccountService accounts;
    private final CodeGuardProperties config;

    public CodeGuardService(GitHubAppClient github, RepoScanner scanner, VirusTotalClient virusTotal,
            CodeFindingRepository findings, EventPublisher events, UserRepository users, AccountService accounts,
            CodeGuardProperties config) {
        this.github = github;
        this.scanner = scanner;
        this.virusTotal = virusTotal;
        this.findings = findings;
        this.events = events;
        this.users = users;
        this.accounts = accounts;
        this.config = config;
    }

    /** push, pull_request or release payload; anything else is ignored. */
    public void handle(String event, JsonNode payload) {
        long installation = payload.path("installation").path("id").asLong();
        String repo = payload.path("repository").path("full_name").asText();
        switch (event) {
            case "push" -> {
                String sha = payload.path("after").asText();
                if (!payload.path("deleted").asBoolean(false) && !sha.matches("0+") && !sha.isEmpty()) {
                    scanCommit(CodeFinding.Trigger.PUSH, installation, repo, sha);
                }
            }
            case "pull_request" -> {
                if (List.of("opened", "synchronize", "reopened").contains(payload.path("action").asText())) {
                    scanCommit(CodeFinding.Trigger.PR, installation, repo,
                            payload.path("pull_request").path("head").path("sha").asText());
                }
            }
            case "release" -> {
                if ("published".equals(payload.path("action").asText())) {
                    scanRelease(installation, repo, payload.path("release"));
                }
            }
            default -> log.debug("Code Guard ignores {} events", event);
        }
    }

    void scanCommit(CodeFinding.Trigger trigger, long installation, String repo, String sha) {
        UUID orgId = organization();
        String token = github.installationToken(installation);
        Path work = null;
        try {
            work = Files.createTempDirectory("obsidian-codeguard-");
            Path source = work.resolve("src");
            github.downloadSnapshot(token, repo, sha, source);
            RepoScanner.Outcome outcome = scanner.scan(source, work);
            List<CodeFinding> saved = new ArrayList<>();
            for (RepoScanner.Found found : outcome.findings()) {
                store(new CodeFinding(orgId, repo, trigger, sha, found.type(), found.severity(), found.ruleId(),
                        found.location(), found.fingerprint())).ifPresent(saved::add);
            }
            github.createCheckRun(token, repo, checkRun(sha, outcome));
            log.info("Code Guard scanned {} @ {}: {} findings ({} new){}", repo, sha.substring(0, Math.min(7, sha.length())),
                    outcome.findings().size(), saved.size(),
                    outcome.unavailableTools().isEmpty() ? "" : ", not installed: " + outcome.unavailableTools());
        } catch (IOException e) {
            throw new IllegalStateException("Code Guard couldn't create its temp dir", e);
        } finally {
            deleteRecursively(work);
        }
    }

    void scanRelease(long installation, String repo, JsonNode release) {
        UUID orgId = organization();
        String token = github.installationToken(installation);
        String tag = release.path("tag_name").asText();
        for (JsonNode asset : release.path("assets")) {
            String name = asset.path("name").asText();
            if (asset.path("size").asLong(0) > config.maxAssetBytes()) {
                log.warn("Code Guard skipped {} in {} {}: larger than {} bytes", name, repo, tag, config.maxAssetBytes());
                continue;
            }
            String sha256 = github.assetSha256(token, asset.path("url").asText());
            VtVerdict verdict = virusTotal.lookupFile(sha256);
            if (verdict.verdict() == Verdict.MALICIOUS || verdict.verdict() == Verdict.SUSPICIOUS) {
                store(new CodeFinding(orgId, repo, CodeFinding.Trigger.RELEASE, tag, CodeFinding.Type.MALICIOUS_BINARY,
                        verdict.verdict() == Verdict.MALICIOUS ? CodeFinding.Severity.CRITICAL : CodeFinding.Severity.MEDIUM,
                        "VT:" + verdict.malicious(), name, null));
            }
            log.info("Code Guard: {} {} asset {} sha256 {} is {}", repo, tag, name, sha256, verdict.verdict());
        }
    }

    private java.util.Optional<CodeFinding> store(CodeFinding finding) {
        if (findings.existsByOrgIdAndRepoFullNameAndGitRefAndFindingTypeAndRuleIdAndLocation(finding.getOrgId(),
                finding.getRepoFullName(), finding.getGitRef(), finding.getFindingType(), finding.getRuleId(),
                finding.getLocation())) {
            return java.util.Optional.empty(); // GitHub redelivered the event, or the same commit was pushed again
        }
        CodeFinding saved = findings.save(finding);
        events.publish(Kind.CODE, saved.getOrgId(), null,
                Map.of("type", "CODE_FINDING", "finding", CodeFindingDto.of(saved)));
        return java.util.Optional.of(saved);
    }

    private Map<String, Object> checkRun(String sha, RepoScanner.Outcome outcome) {
        List<RepoScanner.Found> found = outcome.findings();
        boolean blocking = found.stream().anyMatch(f -> f.severity() == CodeFinding.Severity.CRITICAL
                || f.severity() == CodeFinding.Severity.HIGH);
        String conclusion = blocking ? "failure"
                : !outcome.unavailableTools().isEmpty() && found.isEmpty() ? "neutral" : "success";
        long secrets = found.stream().filter(f -> f.type() == CodeFinding.Type.SECRET).count();
        long vulns = found.size() - secrets;
        StringBuilder summary = new StringBuilder();
        summary.append(secrets).append(" leaked secret(s), ").append(vulns).append(" vulnerable dependenc")
                .append(vulns == 1 ? "y" : "ies").append(".");
        if (secrets > 0) {
            summary.append("\n\nA pushed key is compromised even after it is deleted: rotate it, then mark the finding"
                    + " as rotated in Obsidian.");
        }
        if (!outcome.unavailableTools().isEmpty()) {
            summary.append("\n\nNot run (not installed on the server): ").append(String.join(", ", outcome.unavailableTools()));
        }
        List<Map<String, Object>> annotations = found.stream()
                .sorted(Comparator.comparing(RepoScanner.Found::severity))
                .filter(f -> !f.path().isEmpty())
                .limit(MAX_ANNOTATIONS)
                .map(f -> {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("path", f.path());
                    a.put("start_line", f.line());
                    a.put("end_line", f.line());
                    a.put("annotation_level", f.severity() == CodeFinding.Severity.CRITICAL
                            || f.severity() == CodeFinding.Severity.HIGH ? "failure" : "warning");
                    a.put("title", f.type().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ": " + f.ruleId());
                    a.put("message", f.message());
                    return a;
                })
                .toList();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("title", blocking ? "Security issues found" : found.isEmpty() ? "No issues found" : "Low-severity issues");
        output.put("summary", summary.toString());
        output.put("annotations", annotations);
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("name", CHECK_NAME);
        run.put("head_sha", sha);
        run.put("status", "completed");
        run.put("conclusion", conclusion);
        run.put("output", output);
        return run;
    }

    private UUID organization() {
        Account admin = users.findByEmail(config.orgAdminEmail().trim().toLowerCase(Locale.ROOT)).map(accounts::of)
                .filter(Account::isAdmin)
                .orElseThrow(() -> new IllegalStateException("CODEGUARD_ORG_ADMIN_EMAIL must be an organization admin"));
        return admin.orgId();
    }

    static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Couldn't delete {}", path);
                }
            });
        } catch (IOException e) {
            log.warn("Couldn't clean up {}", root);
        }
    }
}
