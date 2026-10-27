package com.obsidian.codeguard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A code_findings row. A secret's value is never stored: only the detector rule, path:line and a
 * SHA-256 fingerprint, so a repeat leak of the same key can be matched.
 */
@Entity
@Table(name = "code_findings")
public class CodeFinding {

    public enum Trigger { PUSH, PR, RELEASE }
    public enum Type { SECRET, VULN_DEPENDENCY, MALICIOUS_BINARY, VULN_BINARY }
    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW }
    public enum Status { OPEN, REVOKED, FALSE_POSITIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "repo_full_name", nullable = false)
    private String repoFullName;

    @Column(name = "trigger", nullable = false, length = 10)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Trigger trigger;

    @Column(name = "git_ref", nullable = false)
    private String gitRef;

    @Column(name = "finding_type", nullable = false, length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Type findingType;

    @Column(nullable = false, length = 10)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Severity severity;

    @Column(name = "rule_id", nullable = false, length = 120)
    private String ruleId;

    private String location;

    @Column(name = "secret_fingerprint", length = 64)
    private String secretFingerprint;

    @Column(nullable = false, length = 12)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status = Status.OPEN;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected CodeFinding() {}

    public CodeFinding(UUID orgId, String repoFullName, Trigger trigger, String gitRef, Type findingType,
            Severity severity, String ruleId, String location, String secretFingerprint) {
        this.orgId = orgId;
        this.repoFullName = repoFullName;
        this.trigger = trigger;
        this.gitRef = gitRef;
        this.findingType = findingType;
        this.severity = severity;
        this.ruleId = ruleId.length() > 120 ? ruleId.substring(0, 120) : ruleId;
        this.location = location;
        this.secretFingerprint = secretFingerprint;
    }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public String getRepoFullName() { return repoFullName; }
    public Trigger getTrigger() { return trigger; }
    public String getGitRef() { return gitRef; }
    public Type getFindingType() { return findingType; }
    public Severity getSeverity() { return severity; }
    public String getRuleId() { return ruleId; }
    public String getLocation() { return location; }
    public String getSecretFingerprint() { return secretFingerprint; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
