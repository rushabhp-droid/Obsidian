package com.obsidian.scanner;

import com.obsidian.model.Account;
import com.obsidian.model.ScanLogRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ScanController {

    private static final Limit RECENT = Limit.of(200);

    private final ScanService scans;
    private final ScanLogRepository scanLogs;

    public ScanController(ScanService scans, ScanLogRepository scanLogs) {
        this.scans = scans;
        this.scanLogs = scanLogs;
    }

    /** The single scan endpoint for every channel (TODO.md §6). */
    @PostMapping("/scan/unified")
    public ScanResponse scan(Account account, @Valid @RequestBody ScanRequest request) {
        return scans.scan(account, request);
    }

    /** Recent verdicts: admins see their organization's users, everyone else their own. */
    @GetMapping("/scans")
    public List<ScanLogDto> recent(Account account) {
        var logs = account.isAdmin()
                ? scanLogs.findForOrganization(account.orgId(), RECENT)
                : scanLogs.findByUserIdOrderByCreatedAtDesc(account.userId(), RECENT);
        return logs.stream().map(ScanLogDto::of).toList();
    }
}
