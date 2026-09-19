package com.rms.backend.security;

import com.rms.backend.common.SecurityConstants;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.users.entity.User;
import com.rms.backend.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProvisioningService {
    private final EntityManager entityManager;
    private final SecurityProperties configuration;
    private final AuthService auth;
    private final UserRepository users;
    private final PropertyRepository properties;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;

    public record Account(Long id, String username, String email, String fullName, String role, String status) {
        static Account from(User user) {
            return new Account(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getRole(), user.getStatus());
        }
    }

    @Transactional(readOnly = true)
    public boolean setupAvailable() {
        return !configuration.getBootstrapKey().isBlank() && !users.existsByRole(SecurityConstants.ROOT)
                && entityManager.find(RootBootstrap.class, 1L) == null;
    }

    @Transactional
    public Account bootstrap(String key, AuthRequests.CreateAccount dto) {
        String expected = configuration.getBootstrapKey();
        if (expected.isBlank() || key == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            throw new AccessDeniedException("Invalid setup key");
        if (!setupAvailable()) throw new DuplicateResourceException("Root administrator already provisioned");
        entityManager.persist(new RootBootstrap());
        entityManager.flush();
        User root = auth.createAccount(dto, SecurityConstants.ROOT);
        log.info("Security audit: root provisioned userId={}", root.getId());
        return Account.from(root);
    }

    @Transactional
    public Account createOwner(AuthRequests.CreateAccount dto) {
        Access.root();
        User owner = auth.createAccount(dto, SecurityConstants.OWNER);
        log.info("Security audit: owner created actorId={} ownerId={}", Access.current().userId(), owner.getId());
        return Account.from(owner);
    }

    @Transactional(readOnly = true)
    public List<Account> owners() {
        Access.root();
        return users.findByRole(SecurityConstants.OWNER).stream().map(Account::from).toList();
    }

    @Transactional
    public Account setStatus(Long id, String status) {
        Access.root();
        User owner = ownerForUpdate(id);
        owner.setStatus(status);
        owner.setAuthenticationVersion(owner.getAuthenticationVersion() + 1);
        sessions.deleteByUserId(id);
        // Also revoke staff sessions so reactivation does not restore old access tokens.
        users.findByOwnerIdOrId(id, id).forEach(u -> sessions.deleteByUserId(u.getId()));
        log.info("Security audit: owner status changed actorId={} ownerId={} status={}", Access.current().userId(), id, status);
        return Account.from(owner);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        Access.root();
        User owner = ownerForUpdate(id);
        AuthService.validatePassword(password);
        owner.setPasswordHash(passwords.encode(password));
        owner.setAuthenticationVersion(owner.getAuthenticationVersion() + 1);
        owner.setFailedLoginAttempts(0);
        owner.setLockedUntil(null);
        sessions.deleteByUserId(id);
        log.info("Security audit: owner password reset actorId={} ownerId={}", Access.current().userId(), id);
    }

    @Transactional(readOnly = true)
    public List<Property> ownerProperties(Long id) {
        Access.root();
        return properties.findByOwnerId(id);
    }

    private User ownerForUpdate(Long id) {
        return users.findForUpdate(id).filter(u -> SecurityConstants.OWNER.equals(u.getRole()))
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
    }
}
