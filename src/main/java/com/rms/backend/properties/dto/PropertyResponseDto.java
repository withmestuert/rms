package com.rms.backend.properties.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyResponseDto {

    private Long id;
    private String name;
    private String code;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String propertyType;
    private Integer totalFloors;
    private Integer totalRooms;
    private String contactNumber;
    private String contactEmail;
    private String status;
    private Boolean isDeleted;
    private LocalDateTime deletedAt;
    private String deletedBy;
    private String deletionReason;
    private LocalDateTime ttlExpiresAt;
    private Long daysRemaining;
    private Long archiveSnapshotId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PropertyResponseDto(
            Long id, String name, String code, String address, String city, String state, String pincode,
            String propertyType, Integer totalFloors, Integer totalRooms, String contactNumber, String contactEmail,
            String status, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        this(id, name, code, address, city, state, pincode, propertyType, totalFloors, totalRooms,
                contactNumber, contactEmail, status, false, null, null, null, null, null, null, createdAt, updatedAt);
    }
}
