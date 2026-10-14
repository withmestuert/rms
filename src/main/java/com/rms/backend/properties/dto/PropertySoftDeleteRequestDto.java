package com.rms.backend.properties.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertySoftDeleteRequestDto {

    private String confirmationName;
    private String adminPin;
    private String reason;
    private Integer ttlDays = 30;
    private String deletedBy;
}
