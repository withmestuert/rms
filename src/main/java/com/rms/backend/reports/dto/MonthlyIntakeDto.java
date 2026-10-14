package com.rms.backend.reports.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyIntakeDto {
    private String month;
    private int count;
    private String label;
}
