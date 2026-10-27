package com.obsidian.scanner;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Client-side VirusTotal quota: a sliding one-minute window plus a per-UTC-day
 * count (free tier: 4/min, 500/day). In memory, so a restart forgets what was
 * used today; VirusTotal's own 429 then triggers {@link #backOff()}.
 */
public class VtRateLimiter {

    private final int perMinute;
    private final int perDay;
    private final Clock clock;
    private final Deque<Instant> lastMinute = new ArrayDeque<>();
    private LocalDate day;
    private int usedToday;
    private Instant pausedUntil = Instant.MIN;

    public VtRateLimiter(int perMinute, int perDay, Clock clock) {
        this.perMinute = perMinute;
        this.perDay = perDay;
        this.clock = clock;
    }

    /** Takes one request slot, or returns false when the quota is used up. */
    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        if (now.isBefore(pausedUntil)) {
            return false;
        }
        while (!lastMinute.isEmpty() && !lastMinute.peekFirst().isAfter(now.minusSeconds(60))) {
            lastMinute.pollFirst();
        }
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!today.equals(day)) {
            day = today;
            usedToday = 0;
        }
        if (lastMinute.size() >= perMinute || usedToday >= perDay) {
            return false;
        }
        lastMinute.addLast(now);
        usedToday++;
        return true;
    }

    /** VirusTotal answered 429: stop asking for a minute. */
    public synchronized void backOff() {
        pausedUntil = clock.instant().plusSeconds(60);
    }
}
