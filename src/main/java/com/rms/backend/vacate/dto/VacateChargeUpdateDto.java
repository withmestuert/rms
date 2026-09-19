package com.rms.backend.vacate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacateChargeUpdateDto {
    private Double maintenanceCharge;
    private Double breakageCharge;
    private String notes;
}
