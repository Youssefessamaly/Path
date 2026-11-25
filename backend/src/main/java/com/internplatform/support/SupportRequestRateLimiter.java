package com.internplatform.support;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-IP, in-memory sliding-1-hour-window cap on public support-form submissions — abuse/spam
 * protection for an endpoint that, unlike the rest of the API, has no authenticated identity to
 * key on. Same pattern as com.internplatform.screening.ai.anthropic.AiRateLimiter (per-company
 * there, per-IP here); resets on application restart, an accepted tradeoff for a cap whose
 * purpose is bounding abuse, not strict accounting. Thread-safe; cheap enough to call on every
 * request.
 */
@Component
public class SupportRequestRateLimiter {

    private final Clock clock;
    private final Map<String, Deque<Instant>> submissionsByIp = new ConcurrentHashMap<>();

    public SupportRequestRateLimiter() {
        this(Clock.systemUTC());
    }

    public SupportRequestRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * @return true if the submission is allowed (and is now counted against the window); false
     *         if this IP has already reached {@code limitPerHour} submissions in the trailing
     *         hour.
     */
    public boolean tryAcquire(String clientIp, int limitPerHour) {
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        Deque<Instant> window = submissionsByIp.computeIfAbsent(clientIp, ip -> new ConcurrentLinkedDeque<>());
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofHours(1));

        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= limitPerHour) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
