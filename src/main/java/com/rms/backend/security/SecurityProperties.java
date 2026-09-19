package com.rms.backend.security;

import com.rms.backend.common.SecurityConstants;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "rms.security")
@Getter @Setter
public class SecurityProperties {
    private final Environment environment;
    private boolean enabled = true;
    private boolean selfRegistrationEnabled = false;
    private Long testOwnerId;
    private String bootstrapKey = "";
    private Duration tokenTtl = Duration.ofHours(8);
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    public SecurityProperties(Environment environment) { this.environment = environment; }

    @PostConstruct
    public void validate() {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        if (!enabled && (profiles.isEmpty() || !SecurityConstants.TEST_PROFILES.containsAll(profiles))) {
            throw new IllegalStateException("Authentication may only be disabled with exclusively local/test profiles");
        }
        if (!enabled && (testOwnerId == null || testOwnerId <= 0)) {
            throw new IllegalStateException("rms.security.test-owner-id must identify an existing OWNER when authentication is disabled");
        }
        if (tokenTtl == null || tokenTtl.compareTo(Duration.ofMinutes(5)) < 0 || tokenTtl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalStateException("Token TTL must be between 5 minutes and 24 hours");
        }
        if (!bootstrapKey.isEmpty() && bootstrapKey.length() < 32) {
            throw new IllegalStateException("Bootstrap key must contain at least 32 characters");
        }
        if (allowedOrigins == null || allowedOrigins.stream().anyMatch(o -> o.contains("*"))) {
            throw new IllegalStateException("CORS requires explicit allowed origins");
        }
    }
}
