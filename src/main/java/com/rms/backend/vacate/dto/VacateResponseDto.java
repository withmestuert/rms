package com.rms.backend.vacate.dto;

import com.rms.backend.vacate.entity.VacateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacateResponseDto {
    private Long id;
    private String requestId;
    private String tenantUid;
    private String tenantName;
    private String roomNo;
    private String mobileNumber;
    private String aadhaarNo;
    private Long propertyId;
    private LocalDate requestDate;
    private LocalDate expectedLeavingDate;
    private Integer noticeDays;
    private Double advancePaid;
    private Double maintenanceCharge;
    private Double breakageCharge;
    private Double advanceRepayable;
    private VacateStatus status;
    private String reason;
    private String notes;
    private String calculationBreakdown;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
