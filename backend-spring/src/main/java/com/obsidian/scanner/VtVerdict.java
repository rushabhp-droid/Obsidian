package com.obsidian.scanner;

import com.obsidian.model.ThreatCategory;
import com.obsidian.model.Verdict;
import java.time.Instant;

/**
 * What VirusTotal says about one URL or file; also the JSON stored at
 * vt:cache:&lt;id&gt; (docs/protocol.md). PENDING is never cached.
 */
public record VtVerdict(Verdict verdict, int malicious, int suspicious, ThreatCategory category, Instant checkedAt) {

    public static VtVerdict pending() {
        return new VtVerdict(Verdict.PENDING, 0, 0, null, null);
    }

    /** 0 malicious = CLEAN, 1–2 = SUSPICIOUS, 3+ = MALICIOUS; suspicious-only engines also mean SUSPICIOUS. */
    public static VtVerdict fromStats(int malicious, int suspicious, ThreatCategory whenFlagged, Instant checkedAt) {
        Verdict verdict = malicious >= 3 ? Verdict.MALICIOUS
                : malicious >= 1 || suspicious >= 1 ? Verdict.SUSPICIOUS
                : Verdict.CLEAN;
        return new VtVerdict(verdict, malicious, suspicious, verdict == Verdict.CLEAN ? null : whenFlagged, checkedAt);
    }
}
