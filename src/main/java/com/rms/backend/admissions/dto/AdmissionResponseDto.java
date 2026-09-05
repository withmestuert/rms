package com.rms.backend.admissions.dto;

import com.rms.backend.admissions.entity.AdmissionStatus;
import com.rms.backend.tenants.entity.AdvancePaidStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionResponseDto {

    private String admissionNumber;
    private String tenantUid;
    private String tenantName;
    private String aadhaarNo;
    private String mobileNumber;
    private String roomNo;
    private Integer roomRent;
    private Integer advancePaid;
    private AdvancePaidStatus advancePaidStatus;
    private AdmissionStatus status;
    private LocalDate enrollmentDate;
    private String remarks;
    private LocalDateTime confirmedOn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
