package com.obsidian.scanner;

import com.obsidian.model.Channel;
import com.obsidian.model.ScanLog;
import com.obsidian.model.Verdict;
import java.time.Instant;
import java.util.UUID;

/** ScanLog in web-dashboard/src/lib/types.ts. */
public record ScanLogDto(
        UUID id,
        Channel channel,
        String sender,
        String indicatorChecked,
        Verdict verdict,
        int vtPositives,
        Instant createdAt) {

    public static ScanLogDto of(ScanLog log) {
        return new ScanLogDto(log.getId(), log.getChannel(), log.getSender(), log.getIndicatorChecked(),
                log.getVerdict(), log.getVtPositives(), log.getCreatedAt());
    }
}
