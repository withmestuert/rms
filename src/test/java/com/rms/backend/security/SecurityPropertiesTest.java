package com.rms.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class SecurityPropertiesTest {
    private SecurityProperties disabled(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        var configuration = new SecurityProperties(env);
        configuration.setEnabled(false);
        configuration.setTestOwnerId(1L);
        return configuration;
    }
    @Test void secureByDefault() {
        var config = new SecurityProperties(new MockEnvironment());
        assertTrue(config.isEnabled());
        assertFalse(config.isSelfRegistrationEnabled());
        assertDoesNotThrow(config::validate);
    }
    @Test void refusesBypassWithoutProfile() { assertThrows(IllegalStateException.class, () -> disabled().validate()); }
    @Test void refusesProductionBypassEvenWithTestProfile() { assertThrows(IllegalStateException.class, () -> disabled("prod", "test").validate()); }
    @Test void permitsExplicitLocalOrTestBypass() {
        assertDoesNotThrow(() -> disabled("local").validate());
        assertDoesNotThrow(() -> disabled("test").validate());
    }
    @Test void requiresRealOwnerIdentifier() {
        var config = disabled("local"); config.setTestOwnerId(null);
        assertThrows(IllegalStateException.class, config::validate);
    }
    @Test void rejectsWeakBootstrapKeyAndExcessiveTokenLifetime() {
        var config = new SecurityProperties(new MockEnvironment()); config.setBootstrapKey("weak");
        assertThrows(IllegalStateException.class, config::validate);
        config.setBootstrapKey(""); config.setTokenTtl(Duration.ofDays(365));
        assertThrows(IllegalStateException.class, config::validate);
    }
}
