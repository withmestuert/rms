package com.rms.backend.rooms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Id
    private String roomNo;

    private String floor;

    private String roomType;

    private Integer rentPerMonth;

    private Integer occupancy; // Total room capacity

    @Column(name = "current_occupancy", nullable = false)
    @ColumnDefault("0")
    private Integer currentOccupancy;

    @Column(name = "reserved_capacity", nullable = false)
    @ColumnDefault("0")
    private Integer reservedCapacity;

    @Column(name = "property_id")
    private Long propertyId;

    private Boolean available;

    public Room(String roomNo, String floor, String roomType, Integer rentPerMonth, Integer occupancy, Boolean available) {
        this(roomNo, floor, roomType, rentPerMonth, occupancy, null, available);
    }

    public Room(String roomNo, String floor, String roomType, Integer rentPerMonth, Integer occupancy, Long propertyId, Boolean available) {
        this.roomNo = roomNo;
        this.floor = floor;
        this.roomType = roomType;
        this.rentPerMonth = rentPerMonth;
        this.occupancy = occupancy;
        this.propertyId = propertyId;
        this.currentOccupancy = 0;
        this.reservedCapacity = 0;
        this.available = available != null ? available : true;
    }

    @PrePersist
    protected void onCreate() {
        if (this.currentOccupancy == null) {
            this.currentOccupancy = 0;
        }
        if (this.reservedCapacity == null) {
            this.reservedCapacity = 0;
        }
        if (this.available == null) {
            this.available = true;
        }
    }

    public Integer getCapacity() {
        return occupancy;
    }

    public int getEffectiveAvailableCapacity() {
        int cap = (occupancy != null) ? occupancy : 0;
        int curr = (currentOccupancy != null) ? currentOccupancy : 0;
        int res = (reservedCapacity != null) ? reservedCapacity : 0;
        return cap - curr - res;
    }

    public boolean hasAvailableCapacity() {
        return getEffectiveAvailableCapacity() > 0;
    }

    public void recalculateAvailability() {
        this.available = hasAvailableCapacity();
    }
}