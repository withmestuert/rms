package com.rms.backend.common;

import java.util.Set;

public final class SecurityConstants {
    private SecurityConstants() {}
    public static final String ROOT = "ROOT";
    public static final String OWNER = "OWNER";
    public static final String REPRESENTATIVE = "REPRESENTATIVE";
    public static final String SUB_MEMBER = "SUB_MEMBER";
    public static final String TEST_BYPASS = "TEST_BYPASS";
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String BOOTSTRAP_HEADER = "X-Bootstrap-Key";
    public static final String PROPERTY_HEADER = "X-Property-Id";
    public static final int PASSWORD_MIN_LENGTH = 12;
    public static final int PASSWORD_MAX_BYTES = 72;
    public static final int BCRYPT_STRENGTH = 12;
    public static final int TOKEN_BYTES = 32;
    public static final int TOKEN_LENGTH = 43;
    public static final int MAX_LOGIN_FAILURES = 5;
    public static final int LOCKOUT_MINUTES = 15;
    public static final Set<String> ACCOUNT_ROLES = Set.of(ROOT, OWNER, REPRESENTATIVE, SUB_MEMBER);
    public static final Set<String> STAFF_ROLES = Set.of(REPRESENTATIVE, SUB_MEMBER);
    public static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    public static final Set<String> TEST_PROFILES = Set.of("local", "test");
}
