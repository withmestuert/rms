package com.rms.backend.security;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from AuthSession s where s.expiresAt <= :now")
    void deleteExpired(@org.springframework.data.repository.query.Param("now") java.time.Instant now);
    void deleteByUserId(Long userId);
}
