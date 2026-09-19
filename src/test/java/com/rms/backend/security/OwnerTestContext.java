package com.rms.backend.security;
import org.junit.jupiter.api.extension.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.Set;
import java.util.List;
public class OwnerTestContext implements BeforeEachCallback, AfterEachCallback {
    public void beforeEach(ExtensionContext context) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new Access.Principal(99L, 99L, "OWNER", Set.of(1L)), null, List.of()));
    }
    public void afterEach(ExtensionContext context) { SecurityContextHolder.clearContext(); }
}
