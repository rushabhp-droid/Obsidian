package com.obsidian.model;

/**
 * Scan verdicts, worst first. PENDING means "not known yet" and is never shown
 * as safe. BYPASSED is only stored in scan_logs; the scan API reports it as CLEAN.
 */
public enum Verdict {
    MALICIOUS, SUSPICIOUS, PENDING, CLEAN, BYPASSED;

    /** The more severe of two verdicts. */
    public Verdict worst(Verdict other) {
        return other.ordinal() < this.ordinal() ? other : this;
    }
}
