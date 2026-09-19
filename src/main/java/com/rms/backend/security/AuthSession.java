package com.rms.backend.security;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="auth_sessions") @Getter @Setter @NoArgsConstructor
public class AuthSession {
    @Id private String tokenHash;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false) private Instant expiresAt;
    @Column(nullable=false) private long authenticationVersion;
    private Long ownerId;
    private Long ownerAuthenticationVersion;
}
