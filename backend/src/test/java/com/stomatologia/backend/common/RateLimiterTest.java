package com.stomatologia.backend.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final class TestClock extends Clock {
        private long millis = 1_000_000;

        void advance(Duration d) {
            millis += d.toMillis();
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1), clock);

    @Test
    void allowsUpToLimitWithinWindow() {
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
    }

    @Test
    void addressesAreCountedSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("1.1.1.1");
        }
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
    }

    @Test
    void newWindowStartsAfterTimeout() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("1.1.1.1");
        }
        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.retryAfterSeconds("1.1.1.1")).isEqualTo(40);

        clock.advance(Duration.ofSeconds(40));
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
    }
}
