package com.rms.backend.admissions.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionRequestDto {

    // Can be provided if enrolling an existing tenant
    private String tenantUid;

    // Required if creating a new tenant (or can be supplied to update/verify)
    private String name;

    private String aadhaarNo;

    private String mobileNumber;

    private String tenantType; // Working / Student

    private String organizationName;

    private String parentContact;

    @Min(value = 0, message = "Advance paid cannot be negative")
    private Integer advancePaid;

    @Min(value = 0, message = "Standard rent cannot be negative")
    private Integer standardRent;

    @NotBlank(message = "Room number is required")
    private String roomNo;

    private LocalDate enrollmentDate;

    private String remarks;
}
