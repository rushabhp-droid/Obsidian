package com.obsidian.rules;

import com.obsidian.model.Account;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Blacklist / whitelist editor, Report and Bypass (TODO.md §6). Scope comes from the caller's role. */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleService rules;

    public RuleController(RuleService rules) {
        this.rules = rules;
    }

    @GetMapping
    public List<ThreatRuleDto> list(Account account) {
        return rules.visibleRules(account).stream().map(ThreatRuleDto::of).toList();
    }

    @PostMapping("/report")
    public ThreatRuleDto report(Account account, @Valid @RequestBody ReportRequest request) {
        return ThreatRuleDto.of(rules.report(account, request.indicatorType(), request.indicatorValue(),
                request.threatCategory()));
    }

    @PostMapping("/bypass")
    public ThreatRuleDto bypass(Account account, @Valid @RequestBody BypassRequest request) {
        return ThreatRuleDto.of(rules.bypass(account, request.indicatorType(), request.indicatorValue(),
                request.ttlMinutes()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Account account, @PathVariable UUID id) {
        rules.delete(account, id);
        return ResponseEntity.noContent().build();
    }
}
