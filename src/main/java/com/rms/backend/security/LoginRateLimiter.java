package com.rms.backend.security;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Bounded per-instance IP limiter. Account lockout is separately persisted across replicas. */
final class LoginRateLimiter {
    private static final int MAX_KEYS = 10_000;
    private static final int REQUESTS_PER_MINUTE = 30;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Clock clock;
    private record Bucket(long minute, int requests) {}

    LoginRateLimiter() { this(Clock.systemUTC()); }
    LoginRateLimiter(Clock clock) { this.clock = clock; }

    synchronized boolean allow(String address) {
        long minute = clock.millis() / 60_000;
        Bucket previous = buckets.get(address);
        if (previous == null || previous.minute() != minute) {
            buckets.entrySet().removeIf(e -> e.getValue().minute() != minute);
            if (buckets.size() >= MAX_KEYS) return false;
            buckets.put(address, new Bucket(minute, 1));
            return true;
        }
        if (previous.requests() >= REQUESTS_PER_MINUTE) return false;
        buckets.put(address, new Bucket(minute, previous.requests() + 1));
        return true;
    }
}
