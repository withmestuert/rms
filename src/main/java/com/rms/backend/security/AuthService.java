package com.rms.backend.security;

import com.rms.backend.common.SecurityConstants;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.users.entity.User;
import com.rms.backend.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PropertyRepository properties;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final SecurityProperties configuration;
    private final SecureRandom random = new SecureRandom();
    // Valid cost-12 hash used to equalize unknown-user password verification work.
    private static final String DUMMY_HASH = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(
            SecurityConstants.BCRYPT_STRENGTH).encode(java.util.UUID.randomUUID().toString());

    public record Token(String accessToken, String tokenType, Instant expiresAt) {}

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < SecurityConstants.PASSWORD_MIN_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > SecurityConstants.PASSWORD_MAX_BYTES) {
            throw new IllegalArgumentException("Password must contain at least 12 characters and at most 72 UTF-8 bytes");
        }
    }

    @Transactional
    public Token register(AuthRequests.CreateAccount dto) {
        if (!configuration.isSelfRegistrationEnabled()) throw new AccessDeniedException("Self-registration is disabled");
        return issue(createAccount(dto, SecurityConstants.OWNER));
    }

    /** Called only by the authenticated provisioning service or gated self-registration. */
    User createAccount(AuthRequests.CreateAccount dto, String role) {
        validatePassword(dto.password());
        if (users.existsByUsername(dto.username()) || users.existsByEmail(dto.email()))
            throw new DuplicateResourceException("Username or email already registered");
        User user = new User();
        user.setUsername(dto.username());
        user.setEmail(dto.email());
        user.setFullName(dto.fullName());
        user.setRole(role);
        user.setStatus(SecurityConstants.ACTIVE);
        user.setPasswordHash(passwords.encode(dto.password()));
        return users.saveAndFlush(user);
    }

    // Failed-attempt counters must commit even when authentication fails.
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Token login(AuthRequests.Login dto) {
        User user = users.findForLogin(dto.username()).orElse(null);
        String hash = user == null || user.getPasswordHash() == null ? DUMMY_HASH : user.getPasswordHash();
        boolean correct = passwords.matches(dto.password(), hash);
        Instant now = Instant.now();
        if (user == null || !SecurityConstants.ACTIVE.equals(user.getStatus()) || user.getPasswordHash() == null
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!correct) {
            if (user.getLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
            }
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= SecurityConstants.MAX_LOGIN_FAILURES) {
                user.setLockedUntil(now.plus(SecurityConstants.LOCKOUT_MINUTES, ChronoUnit.MINUTES));
            }
            throw new BadCredentialsException("Invalid credentials");
        }
        if (principal(user).isEmpty()) throw new BadCredentialsException("Invalid credentials");
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return issue(user);
    }

    private Token issue(User user) {
        byte[] bytes = new byte[SecurityConstants.TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AuthSession session = new AuthSession();
        session.setTokenHash(hash(token));
        session.setUserId(user.getId());
        session.setAuthenticationVersion(user.getAuthenticationVersion());
        if (user.getOwnerId() != null) {
            User owner = users.findById(user.getOwnerId()).filter(o -> SecurityConstants.ACTIVE.equals(o.getStatus()))
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            session.setOwnerId(owner.getId());
            session.setOwnerAuthenticationVersion(owner.getAuthenticationVersion());
        }
        session.setExpiresAt(Instant.now().plus(configuration.getTokenTtl()));
        sessions.save(session);
        return new Token(token, "Bearer", session.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public Optional<Access.Principal> authenticate(String token) {
        if (token.length() != SecurityConstants.TOKEN_LENGTH) return Optional.empty();
        return sessions.findById(hash(token)).filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .filter(s -> s.getOwnerId() == null || users.findById(s.getOwnerId())
                        .filter(o -> java.util.Objects.equals(s.getOwnerAuthenticationVersion(), o.getAuthenticationVersion())).isPresent())
                .flatMap(s -> users.findById(s.getUserId()).filter(u -> u.getAuthenticationVersion() == s.getAuthenticationVersion()
                        && java.util.Objects.equals(u.getOwnerId(), s.getOwnerId())))
                .flatMap(this::principal);
    }

    private Optional<Access.Principal> principal(User user) {
        if (!SecurityConstants.ACTIVE.equals(user.getStatus()) || !SecurityConstants.ACCOUNT_ROLES.contains(user.getRole()))
            return Optional.empty();
        if (SecurityConstants.ROOT.equals(user.getRole())) {
            return Optional.of(new Access.Principal(user.getId(), null, SecurityConstants.ROOT, Set.of()));
        }
        Long ownerId = SecurityConstants.OWNER.equals(user.getRole()) ? user.getId() : user.getOwnerId();
        if (ownerId == null) return Optional.empty();
        if (!SecurityConstants.OWNER.equals(user.getRole()) && users.findById(ownerId)
                .filter(o -> SecurityConstants.OWNER.equals(o.getRole()) && SecurityConstants.ACTIVE.equals(o.getStatus())).isEmpty())
            return Optional.empty();
        Set<Long> allowed = properties.findByOwnerId(ownerId).stream()
                .filter(p -> SecurityConstants.OWNER.equals(user.getRole()) || user.getPropertyIds().contains(p.getId()))
                .map(p -> p.getId()).collect(Collectors.toSet());
        return Optional.of(new Access.Principal(user.getId(), ownerId, user.getRole(), allowed));
    }

    @Transactional(readOnly = true)
    public Access.Principal testingPrincipal() {
        if (configuration.isEnabled()) throw new IllegalStateException("Testing bypass is disabled");
        User owner = users.findById(configuration.getTestOwnerId())
                .filter(u -> SecurityConstants.OWNER.equals(u.getRole()) && SecurityConstants.ACTIVE.equals(u.getStatus()))
                .orElseThrow(() -> new IllegalStateException("Configured test owner must be an existing active OWNER"));
        var ids = properties.findAll().stream().map(p -> p.getId()).collect(Collectors.toSet());
        return new Access.Principal(owner.getId(), owner.getId(), SecurityConstants.TEST_BYPASS, ids);
    }

    @Transactional
    public void changePassword(AuthRequests.ChangePassword dto) {
        User user = users.findForUpdate(Access.current().userId()).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (user.getPasswordHash() == null || !passwords.matches(dto.currentPassword(), user.getPasswordHash()))
            throw new BadCredentialsException("Invalid credentials");
        validatePassword(dto.newPassword());
        user.setPasswordHash(passwords.encode(dto.newPassword()));
        user.setAuthenticationVersion(user.getAuthenticationVersion() + 1);
        sessions.deleteByUserId(user.getId());
    }

    @Transactional
    public void logout(String token) { sessions.deleteById(hash(token)); }
}
