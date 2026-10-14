package com.rms.backend.reports.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyPerformanceDto {
    private Long id;
    private String name;
    private int totalRooms;
    private long targetRent;
    private long collectedRent;
    private double rate;
    private String status;
}
