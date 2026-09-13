package com.rms.backend.properties.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyRequestDto {

    @NotBlank(message = "Property name is required")
    private String name;

    private String code;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String propertyType; // PG, HOSTEL, COLIVING, APARTMENT
    private Integer totalFloors;
    private Integer totalRooms;
    private String contactNumber;
    private String contactEmail;
    private String status; // ACTIVE, INACTIVE
}
