package com.internplatform.screening.ai.anthropic;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiRateLimiterTest {

    @Test
    void allowsCallsUpToTheDailyLimit() {
        AiRateLimiter limiter = new AiRateLimiter();
        UUID company = UUID.randomUUID();

        assertThat(limiter.tryAcquire(company, 3)).isTrue();
        assertThat(limiter.tryAcquire(company, 3)).isTrue();
        assertThat(limiter.tryAcquire(company, 3)).isTrue();
        assertThat(limiter.tryAcquire(company, 3)).isFalse();
    }

    @Test
    void tracksEachCompanyIndependently() {
        AiRateLimiter limiter = new AiRateLimiter();
        UUID companyA = UUID.randomUUID();
        UUID companyB = UUID.randomUUID();

        assertThat(limiter.tryAcquire(companyA, 1)).isTrue();
        assertThat(limiter.tryAcquire(companyA, 1)).isFalse();
        assertThat(limiter.tryAcquire(companyB, 1)).isTrue();
    }

    @Test
    void oldCallsOutsideTheTwentyFourHourWindowDoNotCountAgainstTheLimit() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AiRateLimiter limiter = new AiRateLimiter(clock);
        UUID company = UUID.randomUUID();

        assertThat(limiter.tryAcquire(company, 1)).isTrue();
        assertThat(limiter.tryAcquire(company, 1)).isFalse();

        clock.advance(Duration.ofHours(25));

        assertThat(limiter.tryAcquire(company, 1)).isTrue();
    }

    @Test
    void rejectsANullCompanyIdRatherThanThrowing() {
        AiRateLimiter limiter = new AiRateLimiter();
        assertThat(limiter.tryAcquire(null, 10)).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
