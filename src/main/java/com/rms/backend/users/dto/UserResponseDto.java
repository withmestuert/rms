package com.rms.backend.users.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

    private Long ownerId;
    private java.util.Set<Long> propertyIds;

    public UserResponseDto(Long id, String username, String email, String fullName, String role,
                           String phone, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id=id; this.username=username; this.email=email; this.fullName=fullName; this.role=role;
        this.phone=phone; this.status=status; this.createdAt=createdAt; this.updatedAt=updatedAt;
    }

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String phone;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
