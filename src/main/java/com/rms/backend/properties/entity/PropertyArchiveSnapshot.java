package com.rms.backend.properties.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "property_archive_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyArchiveSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "property_name", nullable = false)
    private String propertyName;

    @Column(name = "property_code")
    private String propertyCode;

    @Column(name = "archived_by")
    private String archivedBy;

    @Column(name = "deletion_reason")
    private String deletionReason;

    @Column(name = "ttl_days")
    private Integer ttlDays;

    @Column(name = "total_rooms")
    private Integer totalRooms;

    @Column(name = "total_residents")
    private Integer totalResidents;

    @Column(name = "total_revenue_potential")
    private Double totalRevenuePotential;

    @Column(name = "total_advance_held")
    private Double totalAdvanceHeld;

    @Lob
    @Column(name = "snapshot_json", columnDefinition = "TEXT", nullable = false)
    private String snapshotJson;

    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

    @PrePersist
    protected void onCreate() {
        if (this.archivedAt == null) {
            this.archivedAt = LocalDateTime.now();
        }
    }
}
