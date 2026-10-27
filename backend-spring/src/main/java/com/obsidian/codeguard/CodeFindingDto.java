package com.obsidian.codeguard;

import java.time.Instant;
import java.util.UUID;

/** CodeFinding in web-dashboard/src/lib/types.ts. secretFingerprint is a SHA-256, never the secret. */
public record CodeFindingDto(UUID id, String repoFullName, CodeFinding.Trigger trigger, String gitRef,
        CodeFinding.Type findingType, CodeFinding.Severity severity, String ruleId, String location,
        String secretFingerprint, CodeFinding.Status status, Instant createdAt) {

    public static CodeFindingDto of(CodeFinding f) {
        return new CodeFindingDto(f.getId(), f.getRepoFullName(), f.getTrigger(), f.getGitRef(), f.getFindingType(),
                f.getSeverity(), f.getRuleId(), f.getLocation(), f.getSecretFingerprint(), f.getStatus(), f.getCreatedAt());
    }
}
