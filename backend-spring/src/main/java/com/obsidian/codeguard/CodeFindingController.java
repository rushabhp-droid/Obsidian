package com.obsidian.codeguard;

import com.obsidian.audit.AuditService;
import com.obsidian.config.ForbiddenException;
import com.obsidian.model.Account;
import com.obsidian.model.AuditAction;
import com.obsidian.rules.InvalidRuleRequestException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Code Guard findings for SOC admins. */
@RestController
@RequestMapping("/api/v1/code/findings")
public class CodeFindingController {

    public record StatusRequest(CodeFinding.Status status) {}

    private final CodeFindingRepository findings;
    private final AuditService audit;

    public CodeFindingController(CodeFindingRepository findings, AuditService audit) {
        this.findings = findings;
        this.audit = audit;
    }

    @GetMapping
    public List<CodeFindingDto> list(Account account, @RequestParam(required = false) String repo,
            @RequestParam(required = false) CodeFinding.Type type,
            @RequestParam(required = false) CodeFinding.Severity severity,
            @RequestParam(required = false) CodeFinding.Status status) {
        requireAdmin(account);
        return findings.findByOrgIdOrderByCreatedAtDesc(account.orgId(), Limit.of(500)).stream()
                .filter(f -> repo == null || repo.equalsIgnoreCase(f.getRepoFullName()))
                .filter(f -> type == null || type == f.getFindingType())
                .filter(f -> severity == null || severity == f.getSeverity())
                .filter(f -> status == null || status == f.getStatus())
                .map(CodeFindingDto::of)
                .toList();
    }

    /** "Mark rotated" (REVOKED) or "False positive"; both are audited. */
    @PostMapping("/{id}/status")
    @Transactional
    public CodeFindingDto setStatus(Account account, @PathVariable UUID id, @RequestBody StatusRequest request) {
        requireAdmin(account);
        if (request.status() != CodeFinding.Status.REVOKED && request.status() != CodeFinding.Status.FALSE_POSITIVE) {
            throw new InvalidRuleRequestException("status must be REVOKED or FALSE_POSITIVE.");
        }
        CodeFinding finding = findings.findById(id).filter(f -> f.getOrgId().equals(account.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        CodeFinding.Status previous = finding.getStatus();
        finding.setStatus(request.status());
        audit.record(AuditAction.CODE_FINDING_STATUS, account.orgId(), account.userId(), null, Map.of(
                "findingId", id.toString(), "repo", finding.getRepoFullName(), "ruleId", finding.getRuleId(),
                "from", previous.name(), "to", request.status().name()));
        return CodeFindingDto.of(findings.save(finding));
    }

    private static void requireAdmin(Account account) {
        if (!account.isAdmin()) {
            throw new ForbiddenException("Code Guard findings are for organization admins.");
        }
    }
}
