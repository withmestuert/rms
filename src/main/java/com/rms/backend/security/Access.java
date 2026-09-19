package com.rms.backend.security;

import com.rms.backend.common.SecurityConstants;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import java.util.Objects;

/** Request principal contains freshly loaded grants, never client-supplied roles. */
public final class Access {
    private Access() {}
    public record Principal(Long userId, Long ownerId, String role, Set<Long> propertyIds) {
        public Principal { propertyIds = Set.copyOf(propertyIds); }
    }
    public static Principal current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Principal p)) {
            throw new AccessDeniedException("Authentication required");
        }
        return p;
    }
    public static boolean canAccess(Long propertyId) {
        return bypassed() || (propertyId != null && current().propertyIds().contains(propertyId));
    }
    public static void read(Long propertyId) {
        if (!canAccess(propertyId)) throw new AccessDeniedException("Property access denied");
    }
    public static void write(Long propertyId) { writer(); read(propertyId); }
    public static void writer() {
        if (bypassed()) return;
        if (!Set.of(SecurityConstants.OWNER, SecurityConstants.REPRESENTATIVE).contains(current().role()))
            throw new AccessDeniedException("Read-only account");
    }
    public static Long owner() {
        if (bypassed()) return current().ownerId();
        if (!SecurityConstants.OWNER.equals(current().role())) throw new AccessDeniedException("Owner access required");
        return current().userId();
    }
    public static boolean bypassed() { return SecurityConstants.TEST_BYPASS.equals(current().role()); }
    public static void root() {
        if (!bypassed() && !SecurityConstants.ROOT.equals(current().role()))
            throw new AccessDeniedException("Root administrator access required");
    }
    public static void matchingProperty(Long requested, Long actual) {
        if (actual == null) throw new IllegalArgumentException("Referenced resource must belong to a property");
        if (requested != null && !Objects.equals(requested, actual))
            throw new IllegalArgumentException("Property must match the referenced resource");
        read(actual);
    }
}
