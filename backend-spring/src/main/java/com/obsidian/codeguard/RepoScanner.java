package com.obsidian.codeguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.auth.Tokens;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scans an extracted repository with gitleaks (secrets) and OSV-Scanner (vulnerable dependencies).
 * gitleaks' report is read in memory only to fingerprint each secret; the report file lives in the
 * scan's temp dir, which the caller deletes.
 */
public class RepoScanner {

    /** What the scan found, before it is stored; {@code path}/{@code line} feed Check Run annotations. */
    public record Found(CodeFinding.Type type, CodeFinding.Severity severity, String ruleId, String location,
            String fingerprint, String path, int line, String message) {}

    public record Outcome(List<Found> findings, List<String> unavailableTools) {}

    private final ToolRunner tools;
    private final ObjectMapper mapper;
    private final CodeGuardProperties config;

    public RepoScanner(ToolRunner tools, ObjectMapper mapper, CodeGuardProperties config) {
        this.tools = tools;
        this.mapper = mapper;
        this.config = config;
    }

    public Outcome scan(Path source, Path workDir) {
        List<Found> findings = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        Path report = workDir.resolve("gitleaks.json");
        ToolRunner.Result leaks = tools.run(workDir, config.toolTimeout(), config.gitleaks(), "detect", "--no-git",
                "--source", source.toString(), "--report-format", "json", "--report-path", report.toString(),
                "--exit-code", "0", "--no-banner");
        if (!leaks.available()) {
            unavailable.add("gitleaks");
        } else {
            findings.addAll(secrets(report, source));
        }
        ToolRunner.Result osv = tools.run(workDir, config.toolTimeout(), config.osvScanner(), "--format", "json",
                "--recursive", source.toString());
        if (!osv.available()) {
            unavailable.add("osv-scanner");
        } else {
            findings.addAll(vulnerabilities(osv.stdout(), source));
        }
        return new Outcome(findings, unavailable);
    }

    private List<Found> secrets(Path report, Path source) {
        List<Found> out = new ArrayList<>();
        if (!Files.exists(report)) {
            return out;
        }
        try {
            for (JsonNode leak : mapper.readTree(report.toFile())) {
                String path = relative(source, leak.path("File").asText());
                int line = leak.path("StartLine").asInt(1);
                String rule = leak.path("RuleID").asText("secret");
                // The value is hashed and dropped here; it is never stored, logged or returned.
                String fingerprint = Tokens.sha256Hex(leak.path("Secret").asText(""));
                out.add(new Found(CodeFinding.Type.SECRET, CodeFinding.Severity.CRITICAL, rule, path + ":" + line,
                        fingerprint, path, line, "Leaked secret (" + rule + "). Treat it as compromised: rotate it now."
                                + " Removing it from the code doesn't make it safe again."));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("gitleaks wrote an unreadable report", e);
        }
    }

    private List<Found> vulnerabilities(String json, Path source) {
        List<Found> out = new ArrayList<>();
        if (json.isBlank()) {
            return out;
        }
        try {
            for (JsonNode result : mapper.readTree(json).path("results")) {
                String lockfile = relative(source, result.path("source").path("path").asText(""));
                for (JsonNode pkg : result.path("packages")) {
                    String name = pkg.path("package").path("name").asText();
                    String version = pkg.path("package").path("version").asText();
                    for (JsonNode group : pkg.path("groups")) {
                        String id = group.path("ids").path(0).asText("OSV");
                        CodeFinding.Severity severity = severity(group.path("max_severity").asText(""));
                        out.add(new Found(CodeFinding.Type.VULN_DEPENDENCY, severity, id, name + "@" + version, null,
                                lockfile, 1, name + " " + version + " is affected by " + id + " (" + severity + "). "
                                        + "Upgrade to a fixed version."));
                    }
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("osv-scanner printed unreadable JSON", e);
        }
    }

    /** OSV groups carry the highest CVSS score as max_severity. */
    static CodeFinding.Severity severity(String maxSeverity) {
        try {
            double score = Double.parseDouble(maxSeverity);
            return score >= 9 ? CodeFinding.Severity.CRITICAL : score >= 7 ? CodeFinding.Severity.HIGH
                    : score >= 4 ? CodeFinding.Severity.MEDIUM : CodeFinding.Severity.LOW;
        } catch (NumberFormatException e) {
            return switch (maxSeverity.toUpperCase(Locale.ROOT)) {
                case "CRITICAL" -> CodeFinding.Severity.CRITICAL;
                case "HIGH" -> CodeFinding.Severity.HIGH;
                case "LOW" -> CodeFinding.Severity.LOW;
                default -> CodeFinding.Severity.MEDIUM;
            };
        }
    }

    private static String relative(Path source, String file) {
        if (file.isEmpty()) {
            return "";
        }
        Path path = Path.of(file);
        return path.isAbsolute() && path.startsWith(source) ? source.relativize(path).toString() : file;
    }
}
