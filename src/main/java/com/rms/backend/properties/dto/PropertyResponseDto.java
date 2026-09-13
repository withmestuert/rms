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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
