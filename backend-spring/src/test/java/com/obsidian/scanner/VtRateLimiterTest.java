package com.obsidian.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The limiter respects VirusTotal's free-tier limit of four requests per minute. */
class VtRateLimiterTest {

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void allowsFourAMinuteThenRefuses() {
        VtRateLimiter limiter = new VtRateLimiter(4, 500, new MutableClock(Instant.parse("2026-10-27T10:00:00Z")));

        for (int i = 0; i < 4; i++) {
            assertThat(limiter.tryAcquire()).as("request %d", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void aSlotFreesSixtySecondsAfterItWasUsed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-27T10:00:00Z"));
        VtRateLimiter limiter = new VtRateLimiter(4, 500, clock);
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire();
        }

        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire()).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void stopsAtTheDailyQuotaUntilTheNextUtcDay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-27T23:00:00Z"));
        VtRateLimiter limiter = new VtRateLimiter(1000, 3, clock);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire()).isTrue();
        }
        assertThat(limiter.tryAcquire()).isFalse();

        clock.advance(Duration.ofMinutes(59));
        assertThat(limiter.tryAcquire()).isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void aQuotaErrorFromVirusTotalPausesForAMinute() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-27T10:00:00Z"));
        VtRateLimiter limiter = new VtRateLimiter(4, 500, clock);

        limiter.backOff();

        assertThat(limiter.tryAcquire()).isFalse();
        clock.advance(Duration.ofSeconds(60));
        assertThat(limiter.tryAcquire()).isTrue();
    }
}
