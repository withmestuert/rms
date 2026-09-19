package com.rms.backend.users.entity;

import com.rms.backend.common.SecurityConstants;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;

    private Long ownerId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false)
    private int failedLoginAttempts;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false)
    private long authenticationVersion;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.time.Instant lockedUntil;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_properties", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "property_id")
    private java.util.Set<Long> propertyIds = new java.util.HashSet<>();

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "role", nullable = false)
    private String role; // OWNER, REPRESENTATIVE, SUB_MEMBER

    @Column(name = "phone")
    private String phone;

    @Column(name = "status", nullable = false)
    private String status; // ACTIVE, INACTIVE

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = SecurityConstants.ACTIVE;
        }
        if (this.role == null) {
            this.role = SecurityConstants.SUB_MEMBER;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
