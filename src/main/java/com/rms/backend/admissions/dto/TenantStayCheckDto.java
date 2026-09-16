package com.rms.backend.admissions.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantStayCheckDto {

    private boolean exists;
    private boolean hasActiveStay;
    private String activeRoomNo;
    private String tenantUid;
    private String tenantName;
    private String aadhaarNo;
    private String mobileNumber;
    private String tenantType;
    private String organizationName;
    private String parentContact;
    private Integer standardRent;
    private Integer advancePaid;
    private LocalDate lastStayFrom;
    private LocalDate lastStayTo;
    private int totalPreviousStays;
}
