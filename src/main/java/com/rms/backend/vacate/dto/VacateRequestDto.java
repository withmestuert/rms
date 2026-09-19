package com.rms.backend.vacate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacateRequestDto {
    private String tenantUid;
    private String tenantName;
    private String roomNo;
    private String mobileNumber;
    private String aadhaarNo;
    private Long propertyId;
    private LocalDate requestDate;
    private LocalDate expectedLeavingDate;
    private Double advancePaid;
    private Double maintenanceCharge;
    private Double breakageCharge;
    private String reason;
    private String notes;
}
