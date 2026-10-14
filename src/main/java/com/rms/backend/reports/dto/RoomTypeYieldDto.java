package com.rms.backend.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomTypeYieldDto {
    private String roomType;
    private int totalRooms;
    private int totalBeds;
    private int occupiedBeds;
    private int availableBeds;
    private int reservedBeds;
    private double occupancyRate;

    private double averageRentPerBed;
    private double potentialRevenue;
    private double realizedRevenue;
    private double lostRevenue;
    private double revPab; // Realized Revenue / Total Beds of this type
}
