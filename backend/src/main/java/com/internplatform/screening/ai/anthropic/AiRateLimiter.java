package com.internplatform.screening.ai.anthropic;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-company, in-memory sliding-24h-hour-window cap on AI provider calls — abuse/cost
 * protection independent of whatever limit Anthropic itself enforces. Deliberately not
 * backed by a database table for this increment (no new migration needed); resets on
 * application restart, which is an accepted tradeoff for a cap whose purpose is bounding
 * cost/abuse, not strict accounting.
 *
 * Thread-safe; cheap enough to call on every request.
 */
@Component
public class AiRateLimiter {

    private final Clock clock;
    private final Map<UUID, Deque<Instant>> callsByCompany = new ConcurrentHashMap<>();

    public AiRateLimiter() {
        this(Clock.systemUTC());
    }

    public AiRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * @return true if the call is allowed (and is now counted against the window); false if
     *         the company has already reached dailyLimit calls in the trailing 24 hours.
     */
    public boolean tryAcquire(UUID companyProfileId, int dailyLimit) {
        if (companyProfileId == null) {
            return false;
        }
        Deque<Instant> window = callsByCompany.computeIfAbsent(companyProfileId, id -> new ConcurrentLinkedDeque<>());
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofHours(24));

        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= dailyLimit) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
