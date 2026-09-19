package com.rms.backend.security;

import com.rms.backend.common.SecurityConstants;
import jakarta.validation.constraints.*;

public final class AuthRequests {
    private AuthRequests() {}
    public record CreateAccount(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,100}") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(min = SecurityConstants.PASSWORD_MIN_LENGTH, max = SecurityConstants.PASSWORD_MAX_BYTES) String password
    ) {}
    public record Login(@NotBlank @Size(max = 100) String username,
                        @NotBlank @Size(max = SecurityConstants.PASSWORD_MAX_BYTES) String password) {}
    public record ChangePassword(@NotBlank @Size(max = SecurityConstants.PASSWORD_MAX_BYTES) String currentPassword,
                                 @NotBlank @Size(min = SecurityConstants.PASSWORD_MIN_LENGTH, max = SecurityConstants.PASSWORD_MAX_BYTES) String newPassword) {}
    public record ResetPassword(@NotBlank @Size(min = SecurityConstants.PASSWORD_MIN_LENGTH, max = SecurityConstants.PASSWORD_MAX_BYTES) String password) {}
    public record Status(@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}
}
