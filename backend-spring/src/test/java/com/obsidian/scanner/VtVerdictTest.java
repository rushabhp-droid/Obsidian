package com.obsidian.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.obsidian.model.ThreatCategory;
import com.obsidian.model.Verdict;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Thresholds on VirusTotal's malicious count: 0 clean, 1–2 suspicious, 3+ malicious. */
class VtVerdictTest {

    @ParameterizedTest
    @CsvSource({
        "0, 0, CLEAN",
        "1, 0, SUSPICIOUS",
        "2, 0, SUSPICIOUS",
        "3, 0, MALICIOUS",
        "14, 2, MALICIOUS",
        "0, 1, SUSPICIOUS",
    })
    void verdictFollowsTheThresholds(int malicious, int suspicious, Verdict expected) {
        VtVerdict verdict = VtVerdict.fromStats(malicious, suspicious, ThreatCategory.PHISHING, Instant.EPOCH);

        assertThat(verdict.verdict()).isEqualTo(expected);
        assertThat(verdict.category()).isEqualTo(expected == Verdict.CLEAN ? null : ThreatCategory.PHISHING);
    }
}
