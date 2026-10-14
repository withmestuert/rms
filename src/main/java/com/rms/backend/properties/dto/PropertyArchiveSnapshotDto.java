package com.rms.backend.properties.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyArchiveSnapshotDto {

    private Long id;
    private Long propertyId;
    private String propertyName;
    private String propertyCode;
    private String archivedBy;
    private String deletionReason;
    private Integer ttlDays;
    private Integer totalRooms;
    private Integer totalResidents;
    private Double totalRevenuePotential;
    private Double totalAdvanceHeld;
    private String snapshotJson;
    private LocalDateTime archivedAt;
}
