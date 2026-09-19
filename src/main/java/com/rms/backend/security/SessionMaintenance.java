package com.rms.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SessionMaintenance {
    private final AuthSessionRepository sessions;
    @Scheduled(fixedDelayString = "${rms.security.session-cleanup-ms:3600000}")
    @Transactional
    public void removeExpiredSessions() { sessions.deleteExpired(Instant.now()); }
}
